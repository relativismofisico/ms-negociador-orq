package com.negociadororq.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.mapper.EstadoFacturaMapper
import com.negociadororq.application.service.ResolutorFactura
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.enums.EstadoNegociacion
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.ReferenciaFactura
import com.negociadororq.infrastructure.persistence.adapter.FacturaAdapter
import com.negociadororq.infrastructure.persistence.adapter.FacturaPatchAdapter
import com.negociadororq.infrastructure.persistence.adapter.OutboxAdapter
import com.negociadororq.infrastructure.persistence.entity.FacturaDocument
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

/**
 * Fase 28d (C), Mongo 7 real: dos empresas (A y B) cargaron cada una su factura "1". Toda lectura y escritura de
 * negociador va por CUFE y empresas: nada cruza a la factura de la otra empresa.
 */
class DosEmpresasMismoNumeroIntegracionSpec extends Specification {

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    MongoTemplate mongo
    @Shared
    FacturaAdapter facturas
    @Shared
    FacturaPatchAdapter patch
    @Shared
    OutboxAdapter outbox

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        def repos = new MongoRepositoryFactory(mongo)
        facturas = new FacturaAdapter(repos.getRepository(FacturaRepository))
        patch = new FacturaPatchAdapter(new FacturaPatchRepository(mongo, new EstadoFacturaMapper()))
        outbox = new OutboxAdapter(repos.getRepository(OutboxEventRepository),
                new ObjectMapper().registerModule(new JavaTimeModule()))
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection(FacturaDocument)
        mongo.dropCollection(OutboxEventEntity)
        mongo.insert(factura("a", "CUFE-A", "PROV-A"))
        mongo.insert(factura("b", "CUFE-B", "PROV-B"))
    }

    static FacturaDocument factura(String id, String cufe, String proveedor) {
        def d = new FacturaDocument()
        d.id = id
        d.numeroFactura = "1"
        d.cufe = cufe
        d.rutProveedor = proveedor
        d.rutPagador = "PAG"
        d.estadoNegociacion = "SIN_NEGOCIAR"
        d.estadoFirma = "SIN_CESION"
        d
    }

    FacturaDocument doc(String id) {
        mongo.findById(id, FacturaDocument)
    }

    static FacturaNegociada solicitud(String proveedor, String pagador) {
        FacturaNegociada.builder().numeroFactura("1").rutProveedor(proveedor).rutPagador(pagador).build()
    }

    def "A y B negocian su factura 1: cada una resuelve la suya y las escrituras por CUFE no cruzan"() {
        given:
        def resolutor = new ResolutorFactura(facturas)

        when: "A y B resuelven su factura 1 (sin CUFE, con número y RUT)"
        def deA = resolutor.resolverSolicitud(solicitud("PROV-A", "PAG"), "FACTORING", "PROV-A")
        def deB = resolutor.resolverSolicitud(solicitud(null, "PAG"), "FACTORING", "PROV-B")

        then:
        deA.id == "a" && deA.cufe == "CUFE-A"
        deB.id == "b" && deB.cufe == "CUFE-B"

        when: "A guarda la negociación y aprueba; B no hace nada todavía"
        def refA = new ReferenciaFactura("CUFE-A", "PROV-A", "PAG")
        patch.actualizarNegociacionIdPorCufe(refA, "neg-A")
        patch.actualizarEstadosPorCufe(refA, EstadoFirma.CESION, EstadoNegociacion.APROBADA)

        then: "solo la de A cambió"
        doc("a").negociacionId == "neg-A" && doc("a").estadoFirma == "CESION"
        doc("b").negociacionId == null && doc("b").estadoFirma == "SIN_CESION" && doc("b").estadoNegociacion == "SIN_NEGOCIAR"

        when: "después B negocia y rechaza la suya"
        def refB = new ReferenciaFactura("CUFE-B", "PROV-B", "PAG")
        patch.actualizarNegociacionIdPorCufe(refB, "neg-B")
        patch.actualizarEstadoFirmaPorCufe(refB, EstadoFirma.RECHAZADA)

        then: "la de A sigue igual"
        doc("a").negociacionId == "neg-A" && doc("a").estadoFirma == "CESION"
        doc("b").negociacionId == "neg-B" && doc("b").estadoFirma == "RECHAZADA"
    }

    def "B con los RUT de A no toca la factura de A: FACTURA_DE_OTRA_EMPRESA"() {
        when:
        new ResolutorFactura(facturas).resolverSolicitud(solicitud("PROV-A", "PAG"), "FACTORING", "PROV-B")

        then:
        def ex = thrown(com.negociadororq.application.exception.AccionNoPermitidaException)
        ex.codigo == ResolutorFactura.FACTURA_DE_OTRA_EMPRESA
        doc("a").estadoNegociacion == "SIN_NEGOCIAR"
    }

    def "un patch con el CUFE de B y las empresas de A no escribe nada: FACTURA_NO_COINCIDE (#metodo)"() {
        when:
        accion.call(new ReferenciaFactura("CUFE-B", "PROV-A", "PAG"))

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE
        doc("a").estadoFirma == "SIN_CESION" && doc("a").negociacionId == null
        doc("b").estadoFirma == "SIN_CESION" && doc("b").negociacionId == null

        where:
        metodo                     | accion
        "actualizarEstadoFirma"    | { ReferenciaFactura r -> patch.actualizarEstadoFirmaPorCufe(r, EstadoFirma.RECHAZADA) }
        "actualizarEstados"        | { ReferenciaFactura r -> patch.actualizarEstadosPorCufe(r, EstadoFirma.CESION, EstadoNegociacion.APROBADA) }
        "actualizarNegociacionId"  | { ReferenciaFactura r -> patch.actualizarNegociacionIdPorCufe(r, "neg-X") }
    }

    def "liquidacion.solicitada con id determinista por negociación y CUFE: una sola fila, nunca se sobrescribe"() {
        given:
        def id = "liquidacion.solicitada|neg-A|CUFE-A"

        when:
        def primero = outbox.guardarEventoConId(id, "neg-A", "topic-liquidacion", [cufe: "CUFE-A"])
        def fila = mongo.findById(id, OutboxEventEntity)
        fila.enviado = true
        mongo.save(fila)
        def segundo = outbox.guardarEventoConId(id, "neg-A", "topic-liquidacion", [cufe: "CUFE-A"])
        def deB = outbox.guardarEventoConId("liquidacion.solicitada|neg-B|CUFE-B", "neg-B", "topic-liquidacion",
                [cufe: "CUFE-B"])

        then: "la segunda no escribe ni reenvía; la de B (otro CUFE) es otra fila"
        primero && !segundo && deB
        mongo.count(new org.springframework.data.mongodb.core.query.Query(), OutboxEventEntity) == 2
        mongo.findById(id, OutboxEventEntity).enviado
    }
}
