package com.negociadororq.infrastructure.scheduler

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime

/**
 * Fase 28d, Mongo 7 real: política de fallidos del outbox viejo. Un fallido nunca se borra (ni la limpieza diaria ni la
 * alerta lo tocan) y mientras exista alguno queda el ERROR [OUTBOX-FALLIDO] con la cantidad. Desde la A1b el outbox ya
 * no conoce otp.validate: un evento de ese tipo (de antes) pasa a FALLIDO como topic desconocido, sin publicarse.
 */
class OutboxFallidosIntegracionSpec extends Specification {

    static final String MS = "ms-negociador-orq"

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    MongoTemplate mongo
    @Shared
    OutboxEventRepository repository

    OutboxEventPublisher publisher = Mock()
    OutboxScheduler scheduler
    ListAppender<ILoggingEvent> logs = new ListAppender<>()
    Logger logger = (Logger) LoggerFactory.getLogger(OutboxScheduler)

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        repository = new MongoRepositoryFactory(mongo).getRepository(OutboxEventRepository)
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection("outbox_event")
        scheduler = new OutboxScheduler(repository, publisher, new ObjectMapper())
        scheduler.topicRequestOtp = "otp.requested"
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

    OutboxEventEntity guardar(String id, String tipo, String ms, boolean enviado, boolean fallido, LocalDateTime envio) {
        def e = new OutboxEventEntity()
        e.id = id
        e.tipoEvento = tipo
        e.msPropietario = ms
        e.payload = '{"x":1}'
        e.enviado = enviado
        e.fallido = fallido ? Boolean.TRUE : null
        e.fechaCreacion = LocalDateTime.now().minusDays(40)
        e.fechaEnvio = envio
        e.intentos = fallido ? 5 : 0
        repository.save(e)
    }

    def "los fallidos se conservan (ni la limpieza diaria ni la alerta los borran) y queda la alerta con la cantidad"() {
        given:
        guardar("correo-fallido", "notificacion.email", MS, false, true, null)
        guardar("liq-fallido", "liquidacion", MS, false, true, null)
        guardar("correo-enviado-viejo", "notificacion.email", MS, true, false, LocalDateTime.now().minusDays(31))
        guardar("ajeno-fallido", "x", "ms-security-util", false, true, null)

        when:
        scheduler.limpiarEnviados()
        scheduler.alertarFallidos()

        then:
        repository.findById("correo-fallido").isPresent()
        repository.findById("liq-fallido").isPresent()
        repository.findById("ajeno-fallido").isPresent()
        !repository.findById("correo-enviado-viejo").isPresent()
        logs.list.any { it.level.toString() == "ERROR" && it.formattedMessage.contains("[OUTBOX-FALLIDO]")
                && it.formattedMessage.contains(" 2 ") }
    }

    def "sin fallidos no hay alerta"() {
        given:
        guardar("correo-enviado", "notificacion.email", MS, true, false, LocalDateTime.now())

        when:
        scheduler.alertarFallidos()

        then:
        !logs.list.any { it.formattedMessage.contains("[OUTBOX-FALLIDO]") }
    }

    def "Fase 28d (A1b): un otp.validate pendiente de antes pasa a FALLIDO como topic desconocido, sin publicarse"() {
        given:
        guardar("val-viejo", "otp.validate", MS, false, false, null)

        when:
        scheduler.procesarOutbox()

        then:
        0 * publisher._
        with(repository.findById("val-viejo").get()) {
            fallido && motivoFallo == "TOPIC_DESCONOCIDO" && !enviado
        }
    }
}
