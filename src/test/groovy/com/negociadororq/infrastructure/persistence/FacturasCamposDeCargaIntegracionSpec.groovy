package com.negociadororq.infrastructure.persistence

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.service.CupoReversionService
import com.negociadororq.application.service.FacturaServiceImpl
import com.negociadororq.application.service.FacturaVencimientoService
import com.negociadororq.infrastructure.persistence.adapter.FacturaAdapter
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Fase 28d (2.6): la colección "Facturas" la escriben ms-cargaFacturas-neg, ms-negociador-orq y ms-liquidador-neg.
 * Carga guarda campos que el documento de negociador no mapea (contacto de pagador y proveedor, fecha de validación y
 * la marca eventosPendientes / eventosPendientesDesde de la que depende su reconciliador de eventos). Un guardado de
 * documento completo desde negociador reemplaza la factura entera y los borra. Mongo 7 real.
 */
class FacturasCamposDeCargaIntegracionSpec extends Specification {

    static final List<String> CAMPOS_DE_CARGA = ["emailPagador", "emailProveedor", "telefonoPagador",
                                                 "telefonoProveedor", "fechaValidacionFactura", "eventosPendientes",
                                                 "eventosPendientesDesde"]

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

    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 16, 3, 0) }

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        facturas = new FacturaAdapter(new MongoRepositoryFactory(mongo).getRepository(FacturaRepository))
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection("Facturas")
    }

    /** Factura tal como la deja ms-cargaFacturas-neg, con sus campos propios y la marca de eventos pendientes. */
    void facturaDeCarga(String numero, String estadoNegociacion, LocalDate vencimiento) {
        mongo.getCollection("Facturas").insertOne(new Document([
                numeroFactura          : numero,
                rutPagador             : "800",
                rutProveedor           : "900",
                estadoNegociacion      : estadoNegociacion,
                estadoFirma            : "SIN_FIRMA",
                fechavencimientoFactura: Date.from(vencimiento.atStartOfDay().toInstant(ZoneOffset.UTC)),
                emailPagador           : "pagador@ejemplo.test",
                emailProveedor         : "proveedor@ejemplo.test",
                telefonoPagador        : "3000000001",
                telefonoProveedor      : "3000000002",
                fechaValidacionFactura : Date.from(LocalDateTime.of(2026, 11, 1, 8, 0).toInstant(ZoneOffset.UTC)),
                eventosPendientes      : true,
                eventosPendientesDesde : Date.from(LocalDateTime.of(2026, 11, 16, 2, 59).toInstant(ZoneOffset.UTC))]))
    }

    Document leer(String numero) {
        mongo.getCollection("Facturas").find(new Document("numeroFactura", numero)).first()
    }

    List<String> camposPerdidos(String numero) {
        def d = leer(numero)
        CAMPOS_DE_CARGA.findAll { !d.containsKey(it) }
    }

    def "cambiar el estado de firma de una factura conserva los campos de carga"() {
        given:
        facturaDeCarga("FC-1", "NEGOCIADA", LocalDate.of(2027, 1, 30))

        when:
        new FacturaServiceImpl(facturas).actualizarEstadoFirma("FC-1", "FIRMADA")

        then:
        leer("FC-1").getString("estadoFirma") == "FIRMADA"
        camposPerdidos("FC-1") == []
        leer("FC-1").getBoolean("eventosPendientes")
    }

    def "el cambio masivo de estado de firma conserva los campos de carga de cada factura"() {
        given:
        facturaDeCarga("FC-2", "NEGOCIADA", LocalDate.of(2027, 1, 30))
        facturaDeCarga("FC-3", "NEGOCIADA", LocalDate.of(2027, 1, 30))

        when:
        new FacturaServiceImpl(facturas).actualizarEstadoFirmaMasivo(["FC-2", "FC-3"], "FIRMADA")

        then:
        ["FC-2", "FC-3"].every { leer(it).getString("estadoFirma") == "FIRMADA" }
        camposPerdidos("FC-2") == []
        camposPerdidos("FC-3") == []
    }

    def "el proceso nocturno de vencimiento marca VENCIDA sin borrar los campos de carga"() {
        given: "una factura sin negociar que vence en 5 días y otra que no vence"
        facturaDeCarga("FC-4", "SIN_NEGOCIAR", LocalDate.of(2026, 11, 21))
        facturaDeCarga("FC-5", "SIN_NEGOCIAR", LocalDate.of(2027, 3, 1))
        def servicio = new FacturaVencimientoService(facturas, Stub(NegociacionOutputPort), Stub(CompanyOutputPort),
                Mock(CupoReversionService), clock)

        when:
        servicio.actualizarFacturasVencidas()

        then:
        leer("FC-4").getString("estadoNegociacion") == "VENCIDA"
        camposPerdidos("FC-4") == []
        leer("FC-5").getString("estadoNegociacion") == "SIN_NEGOCIAR"
        camposPerdidos("FC-5") == []
    }
}
