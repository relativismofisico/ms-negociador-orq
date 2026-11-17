package com.negociadororq.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.domain.model.ValidacionOtp
import com.negociadororq.infrastructure.client.ValidacionOtpHttpAdapter
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Fase 28d (H): clave de idempotencia idNegociacion|rutEmpresa|rutOperador|n contra un "security" HTTP local que
 * imita la idempotencia real (misma clave: no cuenta otro intento y devuelve el estado actual). Un timeout no avanza
 * n; una respuesta definitiva sí; el código no sale en ningún log.
 */
class ValidadorOtpPendienteSpec extends Specification {

    static final String SECRETO = Base64.encoder.encodeToString(new byte[48].tap { new SecureRandom().nextBytes(it) })
    static final String CORRECTO = "482913"
    static final String INCORRECTO = "000111"

    HttpServer servidor
    /** Estado del "security" local: claves vistas con su respuesta, intentos fallidos, y la demora de la próxima. */
    Map<String, String> vistas = [:]
    List<String> clavesRecibidas = []
    int fallidos = 0
    boolean usado = false
    long demoraProximaMs = 0
    PendingOtpOutputPort pendientes = Mock()
    List<String> logs = []
    Handler manejador = new Handler() {
        void publish(LogRecord r) { logs << r.message }
        void flush() {}
        void close() {}
    }
    ValidadorOtpPendiente validador

    def setup() {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext(ValidacionOtpHttpAdapter.RUTA) { ex ->
            def cuerpo = new ObjectMapper().readValue(ex.requestBody, Map)
            String clave = cuerpo.claveIdempotencia
            clavesRecibidas << clave
            String respuesta = responder(clave, cuerpo.otp as String)
            long demora = demoraProximaMs
            demoraProximaMs = 0
            Thread.sleep(demora)
            def bytes = respuesta.getBytes(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.length)
            ex.responseBody.withCloseable { it.write(bytes) }
        }
        servidor.executor = java.util.concurrent.Executors.newCachedThreadPool()
        servidor.start()
        validador = new ValidadorOtpPendiente(new ValidacionOtpHttpAdapter("http://127.0.0.1:" + servidor.address.port,
                SECRETO, Duration.ofMillis(500)), pendientes)
        Logger.getLogger(ValidadorOtpPendiente.name).addHandler(manejador)
    }

    def cleanup() {
        servidor.stop(0)
        Logger.getLogger(ValidadorOtpPendiente.name).removeHandler(manejador)
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
        usado ? '{"valido":true,"intentosRestantes":0,"otpId":"70","fechaValidacion":"2026-11-17T10:00:00"}'
              : '{"valido":false,"motivo":"INCORRECTO","intentosRestantes":' + (3 - fallidos) + ',"otpId":"70"}'
    }

    static PendingOtp pendiente(Integer n = null) {
        PendingOtp.builder().id("p-1").idNegociacion("NEG-1").rutEmpresa("900123456-7").rutOperador("OP-1")
                .respuestasOtp(n).build()
    }

    def "la clave es idNegociacion|rutEmpresa|rutOperador|n, sin el código; rechaza partes vacías o inseguras"() {
        expect:
        ValidacionOtp.clave("NEG-1", "900123456-7", "OP-1", 0) == "NEG-1|900123456-7|OP-1|0"
        ValidacionOtp.clave("NEG-1", "900123456-7", "OP-1", 3) == "NEG-1|900123456-7|OP-1|3"

        when:
        ValidacionOtp.clave(neg, emp, op, n)

        then:
        thrown(IllegalArgumentException)

        where:
        neg       | emp   | op         | n
        null      | "EMP" | "OP"       | 0
        "NEG"     | " "   | "OP"       | 0
        "NEG"     | "EMP" | "A|B"      | 0
        "NEG"     | "EMP" | "OP X"     | 0
        "NEG"     | "EMP" | "OP"       | -1
        "N" * 145 | "EMP" | "OP"       | 0
    }

    def "reintento tras timeout: misma clave, security no gasta otro intento; n no avanza con el timeout"() {
        given:
        def p = pendiente()
        demoraProximaMs = 1500

        when: "el primer envío llega a security pero la respuesta no vuelve a tiempo"
        validador.validar(p, INCORRECTO, "10.0.0.9")

        then:
        thrown(ServicioDependienteNoDisponibleException)
        0 * pendientes.registrarRespuestaOtp(*_)
        p.respuestasOtp == null

        when: "el usuario reintenta el mismo intento"
        def r = validador.validar(p, INCORRECTO, "10.0.0.9")

        then: "misma clave, un solo intento contado en security"
        clavesRecibidas == ["NEG-1|900123456-7|OP-1|0", "NEG-1|900123456-7|OP-1|0"]
        fallidos == 1
        !r.valido() && r.intentosRestantes() == 2
        1 * pendientes.registrarRespuestaOtp("p-1", 0) >> true
        p.respuestasOtp == 1
    }

    def "segundo intento tras una respuesta inválida: clave n+1, y el código correcto valida"() {
        given:
        def p = pendiente()
        pendientes.registrarRespuestaOtp(*_) >> true

        when:
        def malo = validador.validar(p, INCORRECTO, "ip")
        def bueno = validador.validar(p, CORRECTO, "ip")

        then:
        clavesRecibidas == ["NEG-1|900123456-7|OP-1|0", "NEG-1|900123456-7|OP-1|1"]
        !malo.valido()
        bueno.valido()
        p.respuestasOtp == 2
    }

    def "si se reusara la clave con otro código, security no lo valida (estado actual) y n avanza para el siguiente"() {
        given: "security ya contó la clave 0 con un código incorrecto"
        responder("NEG-1|900123456-7|OP-1|0", INCORRECTO)
        def p = pendiente()
        pendientes.registrarRespuestaOtp(*_) >> true

        when:
        def r = validador.validar(p, CORRECTO, "ip")
        def siguiente = validador.validar(p, CORRECTO, "ip")

        then:
        !r.valido() && r.intentosRestantes() == 2
        siguiente.valido()
        clavesRecibidas == ["NEG-1|900123456-7|OP-1|0", "NEG-1|900123456-7|OP-1|1"]
    }

    def "continúa desde el n persistido y ningún código (ni un hash suyo) sale en los logs"() {
        given:
        def p = pendiente(4)

        when:
        validador.validar(p, CORRECTO, "ip")

        then:
        1 * pendientes.registrarRespuestaOtp("p-1", 4) >> false
        clavesRecibidas == ["NEG-1|900123456-7|OP-1|4"]
        p.respuestasOtp == 5
        logs.any { it.contains("[OTP-HTTP] clave NEG-1|900123456-7|OP-1|4") }
        logs.every { !it.contains(CORRECTO) && !it.contains(CORRECTO.hashCode().toString()) }
    }
}
