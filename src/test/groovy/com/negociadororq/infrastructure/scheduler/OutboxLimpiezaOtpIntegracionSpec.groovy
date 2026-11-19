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
 * Fase 28d (2.4), Mongo 7 real: el código tecleado de otp.validate no se queda en el outbox viejo. Los enviados de ese
 * tipo se borran en la limpieza propia; los fallidos se conservan (con la alerta) y solo pierden el código; los demás
 * tipos, otros servicios y los pendientes no se tocan.
 */
class OutboxLimpiezaOtpIntegracionSpec extends Specification {

    static final String CODIGO = "482913"
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

    static String payloadValidacion() {
        '{"idNegociacion":"NEG-1","rutEmpresa":"EMP-1","rutOperador":"OP-1","otp":"' + CODIGO + '","ipAprobacion":"10.0.0.1"}'
    }

    OutboxEventEntity guardar(String id, String tipo, String ms, boolean enviado, boolean fallido, LocalDateTime envio,
            String payload = payloadValidacion()) {
        def e = new OutboxEventEntity()
        e.id = id
        e.tipoEvento = tipo
        e.msPropietario = ms
        e.payload = payload
        e.enviado = enviado
        e.fallido = fallido ? Boolean.TRUE : null
        e.fechaCreacion = LocalDateTime.now().minusHours(1)
        e.fechaEnvio = envio
        e.intentos = fallido ? 5 : 0
        repository.save(e)
    }

    boolean existe(String id) {
        repository.findById(id).isPresent()
    }

    String payload(String id) {
        repository.findById(id).get().payload
    }

    def "enviados de otp.validate: se borran; fallidos: se conservan sin el código; lo demás no se toca"() {
        given:
        guardar("val-enviado-viejo", "otp.validate", MS, true, false, LocalDateTime.now().minusDays(3))
        guardar("val-enviado-reciente", "otp.validate", MS, true, false, LocalDateTime.now().minusSeconds(5))
        guardar("val-pendiente", "otp.validate", MS, false, false, null)
        guardar("val-fallido", "otp.validate", MS, false, true, null)
        guardar("val-otro-ms", "otp.validate", "ms-security-util", true, false, LocalDateTime.now().minusDays(3))
        guardar("correo-enviado", "notificacion.email", MS, true, false, LocalDateTime.now().minusDays(3), '{"x":1}')
        guardar("correo-fallido", "notificacion.email", MS, false, true, null, '{"x":1}')

        when:
        scheduler.limpiarValidacionesOtp()

        then: "los otp.validate propios enviados ya no están (retención 0)"
        !existe("val-enviado-viejo")
        !existe("val-enviado-reciente")

        and: "el pendiente se conserva tal cual (se publicará y después se borrará)"
        payload("val-pendiente").contains(CODIGO)

        and: "el fallido se conserva, fallido, sin el código y con la marca"
        def fallido = repository.findById("val-fallido").get()
        fallido.fallido
        !fallido.payload.contains(CODIGO)
        new ObjectMapper().readTree(fallido.payload).with { it.get("otp").isNull() && it.get("otpRetirado").asBoolean() &&
                it.get("idNegociacion").asText() == "NEG-1" }

        and: "otros servicios y otros tipos no se tocan (correo enviado sigue con la retención diaria)"
        existe("val-otro-ms")
        existe("correo-enviado")
        existe("correo-fallido")

        and: "alerta por los fallidos (2) sin el código en ningún log"
        logs.list.any { it.level.toString() == "ERROR" && it.formattedMessage.contains("[OUTBOX-FALLIDO]")
                && it.formattedMessage.contains(" 2 ") }
        !logs.list.any { it.formattedMessage.contains(CODIGO) }
    }

    def "repetir la limpieza no vuelve a tocar el fallido ya sin código (idempotente) y nunca lo borra"() {
        given:
        guardar("val-fallido", "otp.validate", MS, false, true, null)
        scheduler.limpiarValidacionesOtp()
        def primero = payload("val-fallido")
        logs.list.clear()

        when:
        scheduler.limpiarValidacionesOtp()

        then:
        payload("val-fallido") == primero
        !logs.list.any { it.formattedMessage.contains("[OUTBOX-OTP-RETIRADO]") }
        existe("val-fallido")
    }

    def "un otp.validate que agota los intentos pasa a FALLIDO ya sin el código"() {
        given:
        def e = guardar("val-agotado", "otp.validate", MS, false, false, null)
        e.intentos = 5
        repository.save(e)

        when:
        scheduler.procesarOutbox()

        then:
        0 * publisher._
        def f = repository.findById("val-agotado").get()
        f.fallido
        !f.payload.contains(CODIGO)
        !logs.list.any { it.formattedMessage.contains(CODIGO) }
    }
}
