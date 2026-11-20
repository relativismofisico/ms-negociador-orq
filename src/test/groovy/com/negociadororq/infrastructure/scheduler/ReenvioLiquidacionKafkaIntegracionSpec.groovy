package com.negociadororq.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent
import com.negociadororq.domain.model.Operacion
import com.negociadororq.infrastructure.kafka.config.TopicsProperties
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher
import com.negociadororq.infrastructure.persistence.adapter.OutboxAdapter
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.test.util.ReflectionTestUtils
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.kafka.KafkaContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime

/**
 * Fase 28d (A4), Mongo 7 y Kafka reales: el reenvío del seguimiento (reencolar el MISMO evento del outbox) publica un
 * mensaje NUEVO en {@code liquidacion.solicitada}, aunque el id del evento sea determinista (una segunda escritura con ese
 * id no hace nada: por eso se reencola y no se vuelve a escribir). Los dos mensajes llevan el mismo payload y la misma
 * clave natural negociacion|cufe|fecha, y ninguno la cabecera eventId: ms-liquidador-neg los consume con esa clave
 * (LiquidacionConsumer.claveConsumo) y procesa uno solo (su LiquidacionAtomicaIntegracionSpec: "una reentrega después
 * del éxito no tiene efecto").
 */
class ReenvioLiquidacionKafkaIntegracionSpec extends Specification {

    static final String TOPIC = "liquidacion.solicitada"

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-kafka-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    OutboxAdapter outbox
    OutboxScheduler scheduler

    def setupSpec() {
        mongoDb.start()
        kafka.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
    }

    def cleanupSpec() {
        cliente?.close()
        kafka.stop()
        mongoDb.stop()
    }

    def setup() {
        def mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        mongo.db.drop()
        def repositorio = new MongoRepositoryFactory(mongo).getRepository(OutboxEventRepository)
        outbox = new OutboxAdapter(repositorio, mapper)
        def topics = new TopicsProperties(otpRequest: "otp.requested", liquidacion: TOPIC,
                notificacionEmail: "notificacion.email", liquidacionFallida: "liquidacion.fallida")
        def productor = new DefaultKafkaProducerFactory<String, Object>([
                (ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)     : kafka.bootstrapServers,
                (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)  : StringSerializer,
                (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG): JsonSerializer])
        def publicador = new OutboxEventPublisher(new KafkaTemplate<String, Object>(productor), topics,
                Stub(ObjectProvider))
        publicador.init()
        scheduler = new OutboxScheduler(repositorio, publicador, mapper)
        ReflectionTestUtils.setField(scheduler, "topicRequestOtp", "otp.requested")
        ReflectionTestUtils.setField(scheduler, "liquidacionFactura", TOPIC)
        ReflectionTestUtils.setField(scheduler, "notificacionEmail", "notificacion.email")
        ReflectionTestUtils.setField(scheduler, "topicAuditoria", "auditoria.accion")
        scheduler.init()
    }

    /** Mensajes de {@code liquidacion.solicitada} de una negociación, leídos desde el principio del topic. */
    List<Map> mensajes(String negociacionId, int esperados) {
        def consumidor = new KafkaConsumer<String, String>([
                (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)       : kafka.bootstrapServers,
                (ConsumerConfig.GROUP_ID_CONFIG)                : "spec-" + UUID.randomUUID(),
                (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)       : "earliest",
                (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)  : StringDeserializer,
                (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG): StringDeserializer])
        def leidos = []
        try {
            consumidor.subscribe([TOPIC])
            def limite = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < limite && leidos.size() < esperados) {
                consumidor.poll(Duration.ofMillis(500)).each { r ->
                    def cuerpo = mapper.readValue(r.value(), Map)
                    if (cuerpo.negociacionId == negociacionId) {
                        leidos << (cuerpo + [eventIdHeader: r.headers().lastHeader("eventId")?.value()])
                    }
                }
            }
            // un poco más, para ver que no llegó nada de más
            consumidor.poll(Duration.ofSeconds(2)).each { r ->
                def cuerpo = mapper.readValue(r.value(), Map)
                if (cuerpo.negociacionId == negociacionId) {
                    leidos << cuerpo
                }
            }
        } finally {
            consumidor.close()
        }
        leidos
    }

    def "el reenvío publica un mensaje nuevo con el mismo payload y la misma clave natural negociacion|cufe|fecha"() {
        given:
        def negociacion = "neg-reenvio-" + UUID.randomUUID()
        def id = "liquidacion.solicitada|" + negociacion + "|CUFE-R1"
        def evento = new LiquidacionSolicitadaEvent(negociacion, "op-1", "F-1", new Operacion("CONFIRMING", "TRADICIONAL"),
                LocalDateTime.of(2026, 11, 20, 10, 0), true, "req-1", "CUFE-R1", "PROV", "PAG")

        when: "la solicitud original"
        def escrita = outbox.guardarEventoConId(id, negociacion, TOPIC, evento)
        scheduler.procesarOutbox()
        def primero = mensajes(negociacion, 1)

        then:
        escrita
        primero.size() == 1

        when: "el id determinista impide volver a escribirla: el reenvío la reencola"
        def otraVez = outbox.guardarEventoConId(id, negociacion, TOPIC, evento)
        def reencolada = outbox.reencolar(id)
        scheduler.procesarOutbox()
        def todos = mensajes(negociacion, 2)

        then: "hay un segundo mensaje en Kafka, idéntico, sin cabecera eventId"
        !otraVez
        reencolada
        todos.size() == 2
        todos.collect { [it.negociacionId, it.cufe, it.fecha] }.unique().size() == 1
        todos[0].findAll { k, v -> k != "eventIdHeader" } == todos[1].findAll { k, v -> k != "eventIdHeader" }
        todos.every { it.eventIdHeader == null }

        when: "sin reencolar no se vuelve a publicar"
        scheduler.procesarOutbox()

        then:
        mensajes(negociacion, 3).size() == 2
    }

    def "reencolar un evento que ya no está en el outbox no publica nada"() {
        expect:
        !outbox.reencolar("liquidacion.solicitada|no-existe|CUFE-X")
    }
}
