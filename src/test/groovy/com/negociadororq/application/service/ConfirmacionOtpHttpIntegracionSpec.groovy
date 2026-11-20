package com.negociadororq.application.service

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto
import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.application.port.output.AlertaFirmaOutputPort
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PersonOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoEndoso
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.infrastructure.client.ValidacionOtpHttpAdapter
import com.negociadororq.infrastructure.pdf.PdfFirmaServiceImpl
import com.negociadororq.infrastructure.pdf.PdfGeneradorAdapter
import com.negociadororq.infrastructure.persistence.adapter.EndosoPatchAdapter
import com.negociadororq.infrastructure.persistence.adapter.PendingOtpAdapter
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository
import com.sun.net.httpserver.HttpServer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Fase 28d (A1), Mongo 7 real (replica set), firma y PDF reales y un "security" HTTP local con la idempotencia de
 * {@code POST /auth/internal/otp/validacion} (misma clave: no cuenta otro intento y devuelve el estado actual):
 * {@code confirmar-otp} valida por HTTP y firma, sin Kafka ni outbox para el código.
 */
class ConfirmacionOtpHttpIntegracionSpec extends Specification {

    static final String NEG = "neg-a1"
    static final String FAC = "FC-A1"
    static final String PAG = "PAG-A1"
    static final String PROV = "PROV-A1"
    static final String FOND = "FOND-A1"
    static final String OP = "op-pag"
    static final String CORRECTO = "482913"
    static final String INCORRECTO = "000111"
    static final String SECRETO = Base64.encoder.encodeToString(new byte[48].tap { new SecureRandom().nextBytes(it) })

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

    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 19, 10, 0, 5) }

    // ─── "security" local ───────────────────────────────────────────────────
    HttpServer servidor
    /** {@code ok}, {@code caido} (503) o {@code lento} (más que el timeout del cliente). */
    volatile String modo = "ok"
    Map<String, String> vistas = [:]
    List<String> clavesRecibidas = Collections.synchronizedList([])
    int fallidos = 0
    boolean usado = false

    // ─── logs ───────────────────────────────────────────────────────────────
    List<String> logs = Collections.synchronizedList([])
    Handler manejador = new Handler() {
        void publish(LogRecord r) { logs << String.valueOf(r.message) + " " + String.valueOf(r.parameters) }
        void flush() {}
        void close() {}
    }
    ListAppender<ILoggingEvent> logsSlf4j = new ListAppender<>()
    private static final List<String> LOGGERS_JUL = [ValidadorOtpPendiente.name, FirmaOtpServiceImpl.name,
                                             AprobacionEndosoDescuentoServiceImpl.name, ConfirmacionOtpServiceImpl.name]

    Endoso endoso
    Negociacion negociacion
    List<Object> eventosOutbox = Collections.synchronizedList([])

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
        mongo.getCollection("pending_otp").createIndex(Indexes.ascending("idNegociacion", "rutEmpresa", "rutOperador"),
                new IndexOptions().unique(true).name("ux_pending_otp_clave")
                        .partialFilterExpression(new Document("rutOperador", new Document('$exists', true))))
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext(ValidacionOtpHttpAdapter.RUTA) { ex ->
            def cuerpo = new ObjectMapper().readValue(ex.requestBody, Map)
            if (modo == "caido") {
                ex.sendResponseHeaders(503, -1)
                ex.close()
                return
            }
            if (modo == "lento") {
                Thread.sleep(1500)
            }
            String clave = cuerpo.claveIdempotencia
            clavesRecibidas << clave
            def bytes = responder(clave, cuerpo.otp as String).getBytes(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.length)
            ex.responseBody.withCloseable { it.write(bytes) }
        }
        servidor.executor = Executors.newCachedThreadPool()
        servidor.start()
        LOGGERS_JUL.each { Logger.getLogger(it).addHandler(manejador) }
        logsSlf4j.start()
        (LoggerFactory.getLogger(ValidacionOtpHttpAdapter) as ch.qos.logback.classic.Logger).addAppender(logsSlf4j)
    }

    def cleanup() {
        servidor.stop(0)
        LOGGERS_JUL.each { Logger.getLogger(it).removeHandler(manejador) }
        (LoggerFactory.getLogger(ValidacionOtpHttpAdapter) as ch.qos.logback.classic.Logger).detachAppender(logsSlf4j)
    }

    /** Como OtpServiceImpl.validarOtpSincrono: la primera vez por clave valida; las siguientes, el estado actual. */
    synchronized String responder(String clave, String otp) {
        if (vistas.containsKey(clave)) {
            return estadoActual()
        }
        if (!usado && otp == CORRECTO) {
            usado = true
        } else if (!usado) {
            fallidos++
        }
        vistas[clave] = estadoActual()
        return vistas[clave]
    }

    String estadoActual() {
        usado ? '{"valido":true,"intentosRestantes":0,"otpId":"70","fechaValidacion":"2026-11-19T15:00:00",' +
                '"instanteValidacion":"2026-11-19T15:00:00Z"}'
              : '{"valido":false,"motivo":"INCORRECTO","intentosRestantes":' + (3 - fallidos) + ',"otpId":"70"}'
    }

    /** Negociación FACTORING con su endoso y PDF; firma el Pagador. Devuelve [aprobación, confirmación]. */
    def armar() {
        def fn = FacturaNegociada.builder().numeroFactura(FAC).cufe("CUFE-" + FAC).rutPagador(PAG).rutProveedor(PROV)
                .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).nombreFondeador("Fondo").build()])
                .aprobaciones([]).estadoFirma(EstadoFirma.EN_REVISION).build()
        negociacion = Negociacion.builder().id(NEG).tipoOperacion(new Operacion("FACTORING", "CON_RESPONSABILIDAD"))
                .facturas([fn]).build()
        def factura = new Factura(numeroFactura: FAC, cufe: "CUFE-" + FAC, rutPagador: PAG, nombrePagador: "Pagador",
                rutProveedor: PROV, nombreProveedor: "Proveedor", valorFactura: 1000000G)
        EndosoOutputPort endosoPort = Stub() {
            findByNegociacionIdAndNumeroFactura(_, _) >> { Optional.ofNullable(endoso) }
            save(_) >> { Endoso e -> endoso = e; e }
        }
        TemplateRenderService plantilla = Stub() {
            renderEndosoFormalTemplate(*_) >> "<html><body><h1>ENDOSO</h1><p>Factura " + FAC + "</p></body></html>"
        }
        UserOutputPort userPort = Stub() { findByUsername(_) >> Optional.empty() }
        def documento = new DocumentoEndosoServiceImpl(plantilla, new PdfGeneradorAdapter(), userPort,
                Stub(PersonOutputPort), clock)
        def firmaEndoso = new FirmaEndosoServiceImpl(endosoPort, new PdfFirmaServiceImpl(), clock, Mock(AuditoriaNegociacionService))
        NegociacionOutputPort negociacionPort = Stub() {
            findById(NEG) >> { Optional.of(negociacion) }
            save(_) >> { Negociacion n -> negociacion = n; n }
        }
        FacturaOutputPort facturaPort = Stub() { findByCufe("CUFE-" + FAC) >> Optional.of(factura) }
        def endosoPatchConMemoria = new EndosoPatchOutputPort() {
            void actualizarEstadoEndosoPorNoFactura(String i, String n, EstadoEndoso e) { }
            void registrarAprobacionActor(String i, String n, AprobacionActorDto a, EstadoEndoso e) {
                endoso.aprobaciones = (endoso.aprobaciones ?: []).findAll { it.rutActor != a.rutActor } + [a]
            }
            boolean reclamarFirmaEmpresa(String i, String n, String r, String o) { endosoPatch.reclamarFirmaEmpresa(i, n, r, o) }
        }
        OutboxService outbox = Stub() {
            guardarEvento(*_) >> { args -> eventosOutbox << args[2]; null }
            guardarEventoConId(*_) >> { args -> eventosOutbox << args[3]; null }
        }
        def aprobacion = new AprobacionEndosoDescuentoServiceImpl(negociacionPort, Mock(FacturaPatchOutputPort),
                endosoPatchConMemoria, outbox, Mock(ReversionExactaService), Mock(CompanyOutputPort),
                Mock(FondeadorQuotaOutputPort), facturaPort, userPort, pendientes, firmaEndoso, Mock(NotificacionService),
                endosoPort, documento, clock, "otp.requested", "liquidacion", Mock(AuditoriaNegociacionService))
        endoso = Endoso.builder().id("end-a1").negociacionId(NEG).numerosFactura([FAC]).aprobaciones([]).firmasOtp([]).build()
        documento.generarDocumentoFormal(endoso, negociacion, [factura], [fn], "op-prov")
        mongo.getCollection("endosos").insertOne(new Document([negociacionId: NEG, numerosFactura: [FAC]]))
        def firma = new FirmaOtpServiceImpl(pendientes, aprobacion, Mock(NotificacionService), Mock(AlertaFirmaOutputPort),
                clock, 3, Duration.ofMinutes(2))
        def validador = new ValidadorOtpPendiente(new ValidacionOtpHttpAdapter("http://127.0.0.1:" + servidor.address.port,
                SECRETO, Duration.ofMillis(500)), pendientes)
        [aprobacion, new ConfirmacionOtpServiceImpl(pendientes, validador, firma)]
    }

    static AprobacionRequestDto pedir() {
        new AprobacionRequestDto(NEG, OP, PAG, "PAGADOR", [new AprobacionFacturaRequestDto(FAC, true, null)], "ok")
    }

    static ConfirmacionOtpRequestDto confirmar(String otp) {
        new ConfirmacionOtpRequestDto(NEG, PAG, OP, otp, "10.0.0.9", null)
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

    PendingOtpDocument pendiente() {
        mongo.findAll(PendingOtpDocument).find { it.rutOperador == OP }
    }

    boolean codigoEnLogs() {
        (logs + logsSlf4j.list*.formattedMessage).any { it.contains(CORRECTO) || it.contains(INCORRECTO) }
    }

    def "código correcto por HTTP: FIRMADO con un bloque (código y referencia de security), clave |0, nada por el outbox"() {
        given:
        def (aprobacion, confirmacion) = armar()
        aprobacion.solicitarOtp(pedir())

        when:
        confirmacion.confirmarOtp(confirmar(CORRECTO))
        def b = bloques(endoso.documentoPdf)

        then:
        pendiente().estado == "FIRMADO"
        pendiente().otp == null
        b.size() == 1
        b[0].containsAll(["Empresa: " + PAG, "OTP: " + CORRECTO, "Referencia OTP: 70"])
        clavesRecibidas == [NEG + "|" + PAG + "|" + OP + "|0"]
        // el código no viaja por el outbox (otp.validate) ni por ningún otro evento
        !eventosOutbox.any { it.toString().contains(CORRECTO) || (it.hasProperty("otp") && it.otp == CORRECTO) }
        !codigoEnLogs()
    }

    def "security caído (503) y lento (timeout): no cambia nada; al volver firma con la misma clave |0 y un solo intento"() {
        given:
        def (aprobacion, confirmacion) = armar()
        aprobacion.solicitarOtp(pedir())

        when: "security responde 503"
        modo = "caido"
        confirmacion.confirmarOtp(confirmar(CORRECTO))

        then:
        thrown(ServicioDependienteNoDisponibleException)
        pendiente().estado == "PENDIENTE_OTP"
        (pendiente().respuestasOtp ?: 0) == 0

        when: "security tarda más que el timeout (cuenta el intento de su lado, pero el negociador no ve la respuesta)"
        modo = "lento"
        confirmacion.confirmarOtp(confirmar(CORRECTO))

        then:
        thrown(ServicioDependienteNoDisponibleException)
        pendiente().estado == "PENDIENTE_OTP"
        (pendiente().respuestasOtp ?: 0) == 0

        when: "security vuelve: el reintento usa la misma clave y recibe la respuesta ya registrada"
        modo = "ok"
        confirmacion.confirmarOtp(confirmar(CORRECTO))

        then:
        pendiente().estado == "FIRMADO"
        bloques(endoso.documentoPdf).size() == 1
        clavesRecibidas.toSet() == [NEG + "|" + PAG + "|" + OP + "|0"] as Set
        fallidos == 0
        !codigoEnLogs()
    }

    def "incorrecto y luego correcto: claves |0 y |1; tras el incorrecto sigue pendiente; después FIRMADO"() {
        given:
        def (aprobacion, confirmacion) = armar()
        aprobacion.solicitarOtp(pedir())

        when:
        confirmacion.confirmarOtp(confirmar(INCORRECTO))

        then:
        pendiente().estado == "PENDIENTE_OTP"
        pendiente().respuestasOtp == 1
        bloques(endoso.documentoPdf).isEmpty()

        when:
        confirmacion.confirmarOtp(confirmar(CORRECTO))

        then:
        pendiente().estado == "FIRMADO"
        clavesRecibidas == [NEG + "|" + PAG + "|" + OP + "|0", NEG + "|" + PAG + "|" + OP + "|1"]
        bloques(endoso.documentoPdf).size() == 1
        !codigoEnLogs()
    }

    def "confirmación repetida después de firmar (evento duplicado): 409 y sin bloque nuevo ni llamada a security"() {
        given:
        def (aprobacion, confirmacion) = armar()
        aprobacion.solicitarOtp(pedir())
        confirmacion.confirmarOtp(confirmar(CORRECTO))
        def llamadas = clavesRecibidas.size()

        when:
        confirmacion.confirmarOtp(confirmar(CORRECTO))

        then:
        def e = thrown(ConflictoException)
        e.codigo == "OTP_SIN_SOLICITUD_PENDIENTE"
        clavesRecibidas.size() == llamadas
        bloques(endoso.documentoPdf).size() == 1
    }

    def "dos confirmaciones simultáneas del mismo código (dos instancias): una sola firma y un solo bloque"() {
        given:
        def (aprobacion, confirmacion) = armar()
        aprobacion.solicitarOtp(pedir())
        def pool = Executors.newFixedThreadPool(2)
        def salida = new CountDownLatch(1)
        def tareas = (1..2).collect {
            pool.submit({
                salida.await()
                try {
                    confirmacion.confirmarOtp(confirmar(CORRECTO)); "ok"
                } catch (ConflictoException e) {
                    e.codigo
                }
            } as Callable)
        }

        when:
        salida.countDown()
        def resultados = tareas*.get(60, TimeUnit.SECONDS)
        pool.shutdown()

        then:
        resultados.count { it == "ok" } >= 1
        resultados.every { it == "ok" || it == "OTP_SIN_SOLICITUD_PENDIENTE" }
        pendiente().estado == "FIRMADO"
        bloques(endoso.documentoPdf).size() == 1
        fallidos == 0
        !codigoEnLogs()
    }
}
