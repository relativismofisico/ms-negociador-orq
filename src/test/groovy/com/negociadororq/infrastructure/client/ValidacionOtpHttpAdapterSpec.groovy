package com.negociadororq.infrastructure.client

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.domain.model.ValidacionOtp
import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime

/**
 * Fase 28d (H b): el cliente de POST /auth/internal/otp/validacion contra un servidor HTTP local. Token de servicio,
 * cuerpo con la IP y la clave, resultado sin el código; security caído, lento o con 5xx es 503 (nada cambió); el
 * código no sale en ningún log.
 */
class ValidacionOtpHttpAdapterSpec extends Specification {

    static final String SECRETO = Base64.encoder.encodeToString(new byte[48].tap { new SecureRandom().nextBytes(it) })

    HttpServer servidor
    Map<String, Object> recibido
    String autorizacion
    int estado = 200
    String respuesta = '{"valido":true,"motivo":null,"intentosRestantes":0,"otpId":"70",' +
            '"fechaValidacion":"2026-11-17T10:00:00","instanteValidacion":"2026-11-17T15:00:00Z"}'
    long demoraMs = 0
    ListAppender<ILoggingEvent> logs = new ListAppender<>()

    def setup() {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext(ValidacionOtpHttpAdapter.RUTA) { ex ->
            recibido = new ObjectMapper().readValue(ex.requestBody, Map)
            autorizacion = ex.requestHeaders.getFirst("Authorization")
            Thread.sleep(demoraMs)
            def bytes = respuesta.getBytes(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(estado, bytes.length)
            ex.responseBody.withCloseable { it.write(bytes) }
        }
        servidor.start()
        logs.start()
        (LoggerFactory.getLogger(ValidacionOtpHttpAdapter) as Logger).addAppender(logs)
    }

    def cleanup() {
        servidor.stop(0)
        (LoggerFactory.getLogger(ValidacionOtpHttpAdapter) as Logger).detachAppender(logs)
    }

    ValidacionOtpHttpAdapter adapter(String base = "http://127.0.0.1:" + servidor.address.port) {
        new ValidacionOtpHttpAdapter(base, SECRETO, Duration.ofMillis(800))
    }

    static ValidacionOtp.Solicitud solicitud() {
        new ValidacionOtp.Solicitud("NEG-1", "EMP", "OP", "123456", "10.0.0.9", "NEG-1|EMP|OP|0")
    }

    def "código correcto: resultado de security; token de servicio y cuerpo con la IP y la clave"() {
        when:
        def r = adapter().validar(solicitud())

        then:
        r.valido()
        r.otpId() == "70"
        r.fechaValidacion() == LocalDateTime.of(2026, 11, 17, 10, 0)
        r.instanteValidacion().toString() == "2026-11-17T15:00:00Z"
        recibido == [idNegociacion: "NEG-1", rutEmpresa: "EMP", rutOperador: "OP", otp: "123456",
                     ipAprobacion: "10.0.0.9", claveIdempotencia: "NEG-1|EMP|OP|0"]
        def claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.decoder.decode(SECRETO))).build()
                .parseSignedClaims(autorizacion.substring("Bearer ".length())).payload
        claims.getSubject() == "ms-negociador-orq"
        claims.get("rol") == "ADMINISTRADOR"
        !solicitud().toString().contains("123456")
        logs.list.every { !it.formattedMessage.contains("123456") }
    }

    def "código incorrecto: valido=false con motivo e intentos restantes"() {
        given:
        respuesta = '{"valido":false,"motivo":"INCORRECTO","intentosRestantes":2,"otpId":"70"}'

        expect:
        with(adapter().validar(solicitud())) {
            !valido() && motivo() == "INCORRECTO" && intentosRestantes() == 2 && fechaValidacion() == null
        }
    }

    def "security caído, con 5xx o lento: 503 (ServicioDependienteNoDisponible) sin el código en el log (#caso)"() {
        given:
        estado = codigo
        demoraMs = demora
        def destino = caido ? "http://127.0.0.1:1" : null

        when:
        (destino ? adapter(destino) : adapter()).validar(solicitud())

        then:
        thrown(ServicioDependienteNoDisponibleException)
        logs.list.any { it.formattedMessage.startsWith("[OTP-HTTP] security no disponible") }
        logs.list.every { !it.formattedMessage.contains("123456") && !it.formattedMessage.contains("127.0.0.1") }

        where:
        caso      | codigo | demora | caido
        "caído"   | 200    | 0      | true
        "5xx"     | 503    | 0      | false
        "timeout" | 200    | 2000   | false
    }

    def "4xx (secreto o servicio no autorizado): error de configuración, no 503"() {
        given:
        estado = 403
        respuesta = '{"error":"Forbidden"}'

        when:
        adapter().validar(solicitud())

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("HTTP 403")
        logs.list.every { !it.formattedMessage.contains("123456") }
    }

    def "sin URL de security o sin secreto: error de configuración claro"() {
        when:
        new ValidacionOtpHttpAdapter(base, secreto, Duration.ofSeconds(1)).validar(solicitud())

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("[OTP-HTTP]")

        where:
        base                   | secreto
        ""                     | SECRETO
        "http://127.0.0.1:1"   | ""
    }
}
