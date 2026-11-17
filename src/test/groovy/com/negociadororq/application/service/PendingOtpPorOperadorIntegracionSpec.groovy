package com.negociadororq.application.service

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.port.output.AlertaFirmaOutputPort
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.ResultadoSolicitudOtp
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.event.OtpValidatedEvent
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.EstadoFirmaOtp
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.infrastructure.pdf.PdfFirmaServiceImpl
import com.negociadororq.infrastructure.pdf.PdfGeneradorAdapter
import com.negociadororq.infrastructure.persistence.adapter.EndosoPatchAdapter
import com.negociadororq.infrastructure.persistence.adapter.PendingOtpAdapter
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fase 28d (2.9 y firma única), Mongo 7 real y PDF real: la solicitud de OTP por (negociación, empresa, operador) con
 * alta atómica, y la firma de FACTORING con Pagador y Fondeador en ambos órdenes y solapados.
 */
class PendingOtpPorOperadorIntegracionSpec extends Specification {

    static final String NEG = "neg-29"
    static final String FAC = "FC-29"
    static final String PAG = "PAG-29"
    static final String PROV = "PROV-29"
    static final String FOND = "FOND-29"

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    MongoTemplate mongo
    @Shared
    PendingOtpAdapter pendientes
    @Shared
    EndosoPatchAdapter endosoPatch

    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 16, 10, 0, 5) }

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        pendientes = new PendingOtpAdapter(new MongoRepositoryFactory(mongo).getRepository(PendingOtpRepository), mongo)
        endosoPatch = new EndosoPatchAdapter(new EndosoPatchRepository(mongo))
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection("pending_otp")
        mongo.dropCollection("endosos")
        // el índice del script manual: único y parcial (solo documentos con rutOperador)
        mongo.getCollection("pending_otp").createIndex(Indexes.ascending("idNegociacion", "rutEmpresa", "rutOperador"),
                new IndexOptions().unique(true).name("ux_pending_otp_clave")
                        .partialFilterExpression(new Document("rutOperador", new Document('$exists', true))))
    }

    static PendingOtp solicitud(String rut, String operador, String rol) {
        PendingOtp.builder().idNegociacion(NEG).rutEmpresa(rut).rutOperador(operador).rolActor(rol)
                .estado(EstadoFirmaOtp.PENDIENTE_OTP.name()).fechaActualizacion(LocalDateTime.of(2026, 11, 16, 10, 0))
                .build()
    }

    long cuantos() {
        mongo.getCollection("pending_otp").countDocuments()
    }

    // ─── alta atómica por la clave ────────────────────────────────────────────

    def "una solicitud repetida del mismo actor deja un solo pendiente (se refresca)"() {
        when:
        def r1 = pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        def r2 = pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))

        then:
        r1.tipo() == ResultadoSolicitudOtp.Tipo.NUEVA
        r2.tipo() == ResultadoSolicitudOtp.Tipo.REFRESCADA
        r2.pendiente().id == r1.pendiente().id
        cuantos() == 1
    }

    def "dos solicitudes simultáneas del mismo actor dejan un solo pendiente"() {
        given:
        def pool = Executors.newFixedThreadPool(8)
        def salida = new CountDownLatch(1)
        def tareas = (1..8).collect {
            pool.submit({ salida.await(); pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR")).tipo() } as java.util.concurrent.Callable)
        }

        when:
        salida.countDown()
        def tipos = tareas*.get(30, TimeUnit.SECONDS)
        pool.shutdown()

        then:
        cuantos() == 1
        tipos.count { it == ResultadoSolicitudOtp.Tipo.NUEVA } == 1
        tipos.count { it == ResultadoSolicitudOtp.Tipo.REFRESCADA } == 7
    }

    def "una solicitud tras FALLO_FIRMA la reemplaza (sin el OTP anterior) y no crea otra"() {
        given:
        def r1 = pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        def fallida = r1.pendiente()
        fallida.estado = EstadoFirmaOtp.FALLO_FIRMA.name(); fallida.otp = "123456"; fallida.otpId = "70"
        fallida.efectosEmitidos = ["CORREO_FIRMA"]; fallida.ultimoError = "x"
        pendientes.save(fallida)

        when:
        def r2 = pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        def doc = mongo.findById(r1.pendiente().id, PendingOtpDocument)

        then:
        r2.tipo() == ResultadoSolicitudOtp.Tipo.REEMPLAZADA
        r2.estadoAnterior() == "FALLO_FIRMA"
        cuantos() == 1
        doc.estado == "PENDIENTE_OTP" && doc.otp == null && doc.otpId == null && doc.efectosEmitidos == null
    }

    def "con el OTP validado (firma en curso) no se toca y se informa FIRMA_EN_CURSO"() {
        given:
        def p = pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR")).pendiente()
        p.estado = EstadoFirmaOtp.OTP_VALIDADO.name(); p.otpId = "70"
        pendientes.save(p)

        expect:
        pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR")).tipo() == ResultadoSolicitudOtp.Tipo.FIRMA_EN_CURSO
        mongo.findById(p.id, PendingOtpDocument).otpId == "70"
    }

    def "dos operadores de la misma negociación tienen cada uno su pendiente; el de uno no sirve para el otro"() {
        when:
        pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        pendientes.registrarSolicitud(solicitud(FOND, "op-fond", "FONDEADOR"))

        then:
        cuantos() == 2
        pendientes.findByClave(NEG, PAG, "op-pag").get().rolActor == "PAGADOR"
        pendientes.findByClave(NEG, FOND, "op-fond").get().rolActor == "FONDEADOR"
        // otro operador de la misma empresa no encuentra el pendiente ajeno
        pendientes.findByClave(NEG, PAG, "op-pag-2").isEmpty()
    }

    def "Fase 28d: dos pendientes activos de la misma negociación nunca se borran entre sí"() {
        given: "Pagador y Fondeador esperan su código a la vez"
        pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        pendientes.registrarSolicitud(solicitud(FOND, "op-fond", "FONDEADOR"))
        def idFond = pendientes.findByClave(NEG, FOND, "op-fond").get().id

        when: "el Pagador vuelve a pedir dos veces, se le busca por su clave y se descarta su pendiente por id"
        pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        pendientes.registrarSolicitud(solicitud(PAG, "op-pag", "PAGADOR"))
        def pag = pendientes.findByClave(NEG, PAG, "op-pag").get()
        pendientes.deleteById(pag.id)

        then: "el del Fondeador sigue ahí, el mismo documento y activo"
        cuantos() == 1
        def fond = pendientes.findByClave(NEG, FOND, "op-fond").get()
        fond.id == idFond
        fond.estado == "PENDIENTE_OTP"

        and: "el puerto ya no ofrece buscar ni borrar por negociación (lo que descartaba los pendientes de otros actores)"
        !(PendingOtpOutputPort.methods*.name.any { it in ["findByIdNegociacion", "deleteByIdNegociacion"] })
    }

    def "pendientes anteriores sin operador: respaldo solo si hay uno; con varios, OTP-AMBIGUO y ninguno"() {
        given:
        def viejo = { String rut -> new Document([idNegociacion: NEG, rutEmpresa: rut, estado: "PENDIENTE_OTP"]) }
        mongo.getCollection("pending_otp").insertOne(viejo(PAG))

        expect: "uno: se usa"
        pendientes.findByClave(NEG, PAG, "op-pag").isPresent()
        pendientes.findByClave(NEG, null, null).isPresent()

        when: "dos anteriores en la negociación y un evento que no identifica al actor"
        mongo.getCollection("pending_otp").insertOne(viejo(FOND))

        then:
        pendientes.findByClave(NEG, null, null).isEmpty()
        pendientes.findByClave(NEG, PAG, "op-pag").isPresent()
    }

    def "reclamo de la firma de la empresa: pasa una vez por OTP; otro OTP de la misma empresa no"() {
        given:
        mongo.getCollection("endosos").insertOne(new Document([negociacionId: NEG, numerosFactura: [FAC]]))

        expect:
        endosoPatch.reclamarFirmaEmpresa(NEG, FAC, PAG, "70")
        endosoPatch.reclamarFirmaEmpresa(NEG, FAC, PAG, "70")      // reintento del mismo OTP
        !endosoPatch.reclamarFirmaEmpresa(NEG, FAC, PAG, "71")     // otro operador de la misma empresa
        endosoPatch.reclamarFirmaEmpresa(NEG, FAC, FOND, "72")     // otra empresa
    }

    // ─── FACTORING de punta a punta: Pagador y Fondeador, PDF real ─────────────

    Endoso endoso
    Negociacion negociacion

    def armar() {
        def fn = FacturaNegociada.builder().numeroFactura(FAC).rutPagador(PAG).rutProveedor(PROV)
                .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).nombreFondeador("Fondo").build()])
                .aprobaciones([]).estadoFirma(EstadoFirma.EN_REVISION).build()
        negociacion = Negociacion.builder().id(NEG).tipoOperacion(new Operacion("FACTORING", "CON_RESPONSABILIDAD"))
                .facturas([fn]).build()
        def factura = new Factura(numeroFactura: FAC, rutPagador: PAG, nombrePagador: "Pagador", rutProveedor: PROV,
                nombreProveedor: "Proveedor", valorFactura: 1000000G)
        EndosoOutputPort endosoPort = Stub() {
            findByNegociacionIdAndNumeroFactura(_, _) >> { Optional.ofNullable(endoso) }
            save(_) >> { Endoso e -> endoso = e; e }
        }
        TemplateRenderService plantilla = Stub() {
            renderEndosoFormalTemplate(*_) >> "<html><body><h1>ENDOSO</h1><p>Factura " + FAC + "</p></body></html>"
        }
        UserOutputPort userPort = Stub() { findByUsername(_) >> Optional.empty() }
        def documento = new DocumentoEndosoServiceImpl(plantilla, new PdfGeneradorAdapter(), userPort,
                Stub(com.negociadororq.application.port.output.PersonOutputPort), clock)
        def firmaEndoso = new FirmaEndosoServiceImpl(endosoPort, new PdfFirmaServiceImpl(), clock, Mock(AuditoriaNegociacionService))
        NegociacionOutputPort negociacionPort = Stub() {
            findById(NEG) >> { Optional.of(negociacion) }
            save(_) >> { Negociacion n -> negociacion = n; n }
        }
        FacturaOutputPort facturaPort = Stub() { findByNumeroFactura(FAC) >> Optional.of(factura) }
        // El reclamo de la firma va a Mongo real; la aprobación registrada se refleja también en el endoso en memoria
        // (donde vive el PDF), como lo deja el patch real en Mongo.
        def endosoPatchConMemoria = new com.negociadororq.application.port.output.EndosoPatchOutputPort() {
            void actualizarEstadoEndosoPorNoFactura(String i, String n, com.negociadororq.domain.enums.EstadoEndoso e) { }
            void registrarAprobacionActor(String i, String n, com.negociadororq.domain.model.AprobacionActorDto a,
                    com.negociadororq.domain.enums.EstadoEndoso e) {
                endoso.aprobaciones = (endoso.aprobaciones ?: []).findAll { it.rutActor != a.rutActor } + [a]
            }
            boolean reclamarFirmaEmpresa(String i, String n, String r, String o) { endosoPatch.reclamarFirmaEmpresa(i, n, r, o) }
        }
        def aprobacion = new AprobacionEndosoDescuentoServiceImpl(negociacionPort, Mock(FacturaPatchOutputPort), endosoPatchConMemoria,
                Mock(OutboxService), Mock(CupoReversionService), Mock(CompanyOutputPort), Mock(FondeadorQuotaOutputPort),
                facturaPort, userPort, pendientes, firmaEndoso, Mock(NotificacionService), endosoPort, documento, clock,
                "topic-otp-request", "topic-otp-validate", "topic-liquidacion", Mock(AuditoriaNegociacionService))
        endoso = Endoso.builder().id("end-29").negociacionId(NEG).numerosFactura([FAC]).aprobaciones([]).firmasOtp([]).build()
        documento.generarDocumentoFormal(endoso, negociacion, [factura], [fn], "op-prov")
        mongo.getCollection("endosos").insertOne(new Document([negociacionId: NEG, numerosFactura: [FAC]]))
        def firma = new FirmaOtpServiceImpl(pendientes, aprobacion, Mock(NotificacionService), Mock(AlertaFirmaOutputPort),
                clock, 3, Duration.ofMinutes(2))
        [aprobacion, firma]
    }

    static AprobacionRequestDto pedir(String rut, String operador, String rol) {
        new AprobacionRequestDto(NEG, operador, rut, rol, [new AprobacionFacturaRequestDto(FAC, true, null)], "ok " + rol)
    }

    static OtpValidatedEvent validacion(String rut, String operador, String otp, String otpId) {
        OtpValidatedEvent.builder().idNegociacion(NEG).rutEmpresa(rut).rutOperador(operador).valido(true).otp(otp)
                .otpId(otpId).ipAprobacion("10.0.0." + otpId).instanteValidacion(Instant.parse("2026-11-16T15:00:00Z"))
                .build()
    }

    static List<List<String>> bloques(byte[] pdf) {
        List<String> l = PDDocument.load(pdf).withCloseable { d -> new PDFTextStripper().getText(d).readLines()*.trim().findAll { it } }
        def salida = []
        for (int i = 0; i < l.size(); i++) {
            if (l[i] == "===== FIRMA ELECTRONICA =====") {
                salida << l.subList(i, Math.min(l.size(), i + 11))
            }
        }
        salida
    }

    def "FACTORING #orden: cada actor firma su propio bloque, 2 bloques de 11 líneas sin contaminación cruzada"() {
        given:
        def (aprobacion, firma) = armar()

        when:
        pasos.each { paso ->
            if (paso == "pide-pag") { aprobacion.solicitarOtp(pedir(PAG, "op-pag", "PAGADOR")) }
            if (paso == "pide-fond") { aprobacion.solicitarOtp(pedir(FOND, "op-fond", "FONDEADOR")) }
            if (paso == "valida-pag") { firma.procesarValidacion(validacion(PAG, "op-pag", "222222", "80")) }
            if (paso == "valida-fond") { firma.procesarValidacion(validacion(FOND, "op-fond", "111111", "81")) }
        }
        def b = bloques(endoso.documentoPdf)
        def pag = b.find { it.contains("Actor: PAGADOR") }
        def fond = b.find { it.contains("Actor: FONDEADOR") }

        then:
        b.size() == 2
        b.every { it.size() == 11 && it[10].startsWith("Obs:") }
        pag.containsAll(["Empresa: " + PAG, "OTP: 222222", "Referencia OTP: 80"])
        fond.containsAll(["Empresa: " + FOND, "OTP: 111111", "Referencia OTP: 81"])
        !pag.any { it.contains("111111") || it.contains(FOND) }
        !fond.any { it.contains("222222") || it.contains(PAG) }
        pendientes.findByClave(NEG, PAG, "op-pag").get().estado == "FIRMADO"
        pendientes.findByClave(NEG, FOND, "op-fond").get().estado == "FIRMADO"
        cuantos() == 2

        where:
        orden                         | pasos
        "Pagador y luego Fondeador"   | ["pide-pag", "valida-pag", "pide-fond", "valida-fond"]
        "Fondeador y luego Pagador"   | ["pide-fond", "valida-fond", "pide-pag", "valida-pag"]
        "solapados (ambos piden antes)" | ["pide-pag", "pide-fond", "valida-fond", "valida-pag"]
    }
}
