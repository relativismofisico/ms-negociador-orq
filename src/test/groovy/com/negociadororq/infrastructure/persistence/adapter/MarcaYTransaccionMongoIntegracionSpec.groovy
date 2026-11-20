package com.negociadororq.infrastructure.persistence.adapter

import com.factoring.commons.outbox.EventoOutbox
import com.factoring.commons.outbox.OutboxPort
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.application.port.output.MarcaNegociacionOutputPort
import com.negociadororq.domain.event.NotificacionEmailEvent
import com.negociadororq.infrastructure.outbox.CorreoTransaccionalAdapter
import com.negociadororq.infrastructure.outbox.OutboxNegociadorConfig
import org.bson.Document
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fase 28d (A2), Mongo 7 real (replica set): el marcador único por negociación y la transacción Mongo propia.
 */
class MarcaYTransaccionMongoIntegracionSpec extends Specification {

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    MongoTemplate mongo
    @Shared
    SimpleMongoClientDatabaseFactory fabrica

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        fabrica = new SimpleMongoClientDatabaseFactory(cliente, "negociador")
        mongo = new MongoTemplate(fabrica)
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection("negociacion_marca")
        mongo.dropCollection("negociacion_prueba")
        mongo.createCollection("negociacion_marca")
        mongo.createCollection("negociacion_prueba")
    }

    def "marcador: el primero entra, el segundo con el mismo id choca; buscar devuelve el que entró"() {
        given:
        def marca = new MarcaNegociacionAdapter(mongo)

        expect:
        marca.crear("neg-1", MarcaNegociacionOutputPort.Tipo.CANCELADA)
        !marca.crear("neg-1", MarcaNegociacionOutputPort.Tipo.CREADA)
        marca.buscar("neg-1") == Optional.of(MarcaNegociacionOutputPort.Tipo.CANCELADA)
        marca.buscar("neg-x") == Optional.empty()
    }

    def "#veces carreras CREADA contra CANCELADA con el mismo id: entra exactamente uno"() {
        given:
        def marca = new MarcaNegociacionAdapter(mongo)
        def pool = Executors.newFixedThreadPool(2)
        int creadas = 0, canceladas = 0

        when:
        veces.times { n ->
            def salida = new CountDownLatch(1)
            def a = pool.submit({ salida.await(); marca.crear("neg-c" + n, MarcaNegociacionOutputPort.Tipo.CREADA) } as Callable)
            def b = pool.submit({ salida.await(); marca.crear("neg-c" + n, MarcaNegociacionOutputPort.Tipo.CANCELADA) } as Callable)
            salida.countDown()
            boolean ra = a.get(30, TimeUnit.SECONDS), rb = b.get(30, TimeUnit.SECONDS)
            assert ra ^ rb
            if (ra) creadas++ else canceladas++
        }
        pool.shutdown()

        then:
        creadas + canceladas == veces

        where:
        veces = 50
    }

    def "transacción Mongo: confirma junto; si la acción falla (o el marcador choca) no queda nada escrito"() {
        given:
        def tx = new TransaccionMongoAdapter(fabrica)
        def marca = new MarcaNegociacionAdapter(mongo)

        when: "confirma"
        tx.ejecutar {
            marca.crear("neg-ok", MarcaNegociacionOutputPort.Tipo.CREADA)
            mongo.insert(new Document("_id", "neg-ok"), "negociacion_prueba")
        }

        then:
        marca.buscar("neg-ok").isPresent()
        mongo.getCollection("negociacion_prueba").countDocuments(new Document("_id", "neg-ok")) == 1

        when: "la acción falla después de escribir"
        tx.ejecutar {
            marca.crear("neg-mal", MarcaNegociacionOutputPort.Tipo.CREADA)
            mongo.insert(new Document("_id", "neg-mal"), "negociacion_prueba")
            throw new IllegalStateException("caída a mitad")
        }

        then:
        thrown(IllegalStateException)
        marca.buscar("neg-mal").isEmpty()
        mongo.getCollection("negociacion_prueba").countDocuments(new Document("_id", "neg-mal")) == 0

        when: "el reconciliador ya puso CANCELADA: la creación lenta escribe la negociación y su marcador choca"
        marca.crear("neg-lenta", MarcaNegociacionOutputPort.Tipo.CANCELADA)
        tx.ejecutar {
            mongo.insert(new Document("_id", "neg-lenta"), "negociacion_prueba")
            if (!marca.crear("neg-lenta", MarcaNegociacionOutputPort.Tipo.CREADA)) {
                throw new IllegalStateException("cancelada")
            }
        }

        then: "no queda la negociación"
        thrown(IllegalStateException)
        mongo.getCollection("negociacion_prueba").countDocuments(new Document("_id", "neg-lenta")) == 0
        marca.buscar("neg-lenta") == Optional.of(MarcaNegociacionOutputPort.Tipo.CANCELADA)
    }

    def "correo transaccional: registra con el id propio por el OutboxPort de commons; sin outbox, error claro"() {
        given:
        OutboxPort outbox = Mock()
        def evento = NotificacionEmailEvent.builder().idNegociacion("neg-1").build()

        when:
        def ok = new CorreoTransaccionalAdapter(Stub(ObjectProvider) { getIfAvailable() >> outbox }).registrar("id-1", evento)

        then:
        1 * outbox.registrar(OutboxNegociadorConfig.TIPO_NOTIFICACION_EMAIL, "id-1", evento) >> Optional.of(Mock(EventoOutbox))
        ok

        when:
        new CorreoTransaccionalAdapter(Stub(ObjectProvider) { getIfAvailable() >> null }).registrar("id-2", evento)

        then:
        thrown(IllegalStateException)
    }
}
