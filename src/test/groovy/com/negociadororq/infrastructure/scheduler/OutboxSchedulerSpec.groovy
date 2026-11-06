package com.negociadororq.infrastructure.scheduler

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import spock.lang.Specification

import java.time.LocalDateTime

/** Outbox de negociador: sin pérdida silenciosa, solo los pendientes propios, limpieza de enviados antiguos. */
class OutboxSchedulerSpec extends Specification {

    static final String PAYLOAD_SENSIBLE = '{"destinatario":"persona@correo.test","codigo":"123456"}'

    OutboxEventRepository repository = Mock()
    OutboxEventPublisher publisher = Mock()
    ObjectMapper objectMapper = Mock()
    OutboxScheduler scheduler = new OutboxScheduler(repository, publisher, objectMapper)

    ListAppender<ILoggingEvent> logs = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(OutboxScheduler)

    def setup() {
        scheduler.topicRequestOtp = "otp.request"
        scheduler.topicValidateOtp = "otp.validate"
        scheduler.topicValidateResponse = "otp.validate.response"
        scheduler.liquidacionFactura = "liquidacion"
        scheduler.notificacionEmail = "notificacion.email"
        scheduler.topicAuditoria = "auditoria.accion"
        scheduler.init()
        logs.start()
        logger.addAppender(logs)
    }

    def cleanup() {
        logger.detachAppender(logs)
    }

    private static OutboxEventEntity evento(String topic, int intentos = 0) {
        OutboxEventEntity.builder().id("evt-1").msPropietario("ms-negociador-orq").tipoEvento(topic)
                .payload(PAYLOAD_SENSIBLE).enviado(false).intentos(intentos).fechaCreacion(LocalDateTime.now()).build()
    }

    def "solo consulta los pendientes propios y no fallidos, del más antiguo al más nuevo, de a 50"() {
        when:
        scheduler.procesarOutbox()

        then:
        1 * repository.findPendientes("ms-negociador-orq", { Pageable p ->
            p.pageSize == 50 && p.sort.getOrderFor("fechaCreacion")?.ascending
        }) >> []
        0 * publisher._
    }

    def "agotados los intentos pasa a FALLIDO (no a enviado) con log de error sin el payload"() {
        given:
        def e = evento("notificacion.email", OutboxScheduler.MAX_INTENTOS)
        repository.findPendientes(_, _) >> [e]

        when:
        scheduler.procesarOutbox()

        then:
        0 * publisher._
        1 * repository.save(e)
        e.fallido
        !e.enviado
        e.fechaEnvio == null
        e.motivoFallo == "MAX_INTENTOS"
        e.fechaFallo != null
        def error = logs.list.find { it.level.toString() == "ERROR" }
        error.formattedMessage.contains("FALLIDO")
        error.formattedMessage.contains("evt-1")
        !logs.list*.formattedMessage.any { it.contains("persona@correo.test") || it.contains("123456") }
    }

    def "un topic desconocido pasa a FALLIDO, no a enviado"() {
        given:
        def e = evento("topic.que.no.existe")
        repository.findPendientes(_, _) >> [e]

        when:
        scheduler.procesarOutbox()

        then:
        0 * publisher._
        1 * repository.save(e)
        e.fallido
        !e.enviado
        e.motivoFallo == "TOPIC_DESCONOCIDO"
    }

    def "si Kafka no confirma, suma un intento y sigue pendiente"() {
        given:
        def e = evento("notificacion.email", 1)
        repository.findPendientes(_, _) >> [e]
        objectMapper.readValue(_ as String, _ as Class) >> new Object()
        publisher.publicar(_) >> { throw new IllegalStateException("Kafka no confirmó el evento") }

        when:
        scheduler.procesarOutbox()

        then:
        1 * repository.save(e)
        e.intentos == 2
        !e.enviado
        !e.fallido
    }

    def "con el ack, se marca enviado"() {
        given:
        def e = evento("notificacion.email")
        repository.findPendientes(_, _) >> [e]
        objectMapper.readValue(_ as String, _ as Class) >> new Object()

        when:
        scheduler.procesarOutbox()

        then:
        1 * publisher.publicar(_)
        1 * repository.save(e)
        e.enviado
        e.fechaEnvio != null
        !e.fallido
    }

    def "limpiarEnviados borra solo los enviados propios con más de la retención"() {
        given:
        scheduler.retencionDias = dias
        def antes = LocalDateTime.now().minusDays(dias)

        when:
        scheduler.limpiarEnviados()

        then:
        1 * repository.deleteByMsPropietarioAndEnviadoTrueAndFechaEnvioBefore("ms-negociador-orq", { LocalDateTime l ->
            !l.isBefore(antes.minusSeconds(5)) && !l.isAfter(LocalDateTime.now().minusDays(dias))
        }) >> 4

        where:
        dias << [30, 7]
    }
}
