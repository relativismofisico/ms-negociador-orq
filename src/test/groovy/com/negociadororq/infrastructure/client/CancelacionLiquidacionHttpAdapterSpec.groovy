package com.negociadororq.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.application.port.output.CancelacionLiquidacionOutputPort
import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration

/** Fase 28d (A4): "cancelar si no procesada" en el liquidador, con el JWT de servicio, contra un servidor HTTP real. */
class CancelacionLiquidacionHttpAdapterSpec extends Specification {

    private static final byte[] CLAVE = new byte[48].tap { new SecureRandom().nextBytes(it) }
    private static final String SECRETO = Base64.encoder.encodeToString(CLAVE)

    HttpServer servidor
    Map<String, Object> recibido
    String autorizacion
    int estado = 200
    String respuesta = '{"negociacionId":"neg-1","cufe":"CUFE-1","estado":"CANCELADA"}'
    long demoraMs = 0

    def setup() {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext(CancelacionLiquidacionHttpAdapter.RUTA) { ex ->
            recibido = new ObjectMapper().readValue(ex.requestBody, Map)
            autorizacion = ex.requestHeaders.getFirst("Authorization")
            Thread.sleep(demoraMs)
            def bytes = respuesta.getBytes(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(estado, bytes.length)
            ex.responseBody.withCloseable { it.write(bytes) }
        }
        servidor.start()
    }

    def cleanup() {
        servidor.stop(0)
    }

    CancelacionLiquidacionHttpAdapter adapter(String base = "http://127.0.0.1:" + servidor.address.port) {
        new CancelacionLiquidacionHttpAdapter(base, SECRETO, Duration.ofMillis(800))
    }

    def "#estadoLiquidador del liquidador: se devuelve tal cual; token de servicio de ms-negociador-orq y cuerpo"() {
        given:
        respuesta = '{"estado":"' + estadoLiquidador + '"}'

        when:
        def r = adapter().cancelar("neg-1", "CUFE-1")

        then:
        r == CancelacionLiquidacionOutputPort.Resultado.valueOf(estadoLiquidador)
        recibido == [negociacionId: "neg-1", cufe: "CUFE-1"]
        def claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(CLAVE)).build()
                .parseSignedClaims(autorizacion.substring("Bearer ".length())).payload
        claims.getSubject() == "ms-negociador-orq"
        claims.get("rol") == "ADMINISTRADOR"

        where:
        estadoLiquidador << ["LIQUIDADA", "FALLIDA", "CANCELADA"]
    }

    def "caído, timeout, 5xx, 4xx, sin URL o respuesta inválida: no disponible (se reintenta)"() {
        given:
        estado = codigo
        respuesta = cuerpo
        demoraMs = demora

        when:
        (sinUrl ? adapter("") : adapter()).cancelar("neg-1", "CUFE-1")

        then:
        thrown(ServicioDependienteNoDisponibleException)

        where:
        codigo | cuerpo                  | demora | sinUrl
        500    | '{"mensaje":"x"}'       | 0      | false
        403    | '{"mensaje":"x"}'       | 0      | false
        200    | '{"estado":"RARO"}'     | 0      | false
        200    | '{"otro":1}'            | 0      | false
        200    | '{"estado":"CANCELADA"}' | 1500  | false
        200    | '{"estado":"CANCELADA"}' | 0     | true
    }

    def "liquidador apagado (nadie escucha): no disponible"() {
        given: "un puerto libre en el que nadie escucha"
        def libre = new ServerSocket(0)
        def puerto = libre.localPort
        libre.close()

        when:
        adapter("http://127.0.0.1:" + puerto).cancelar("neg-1", "CUFE-1")

        then:
        thrown(ServicioDependienteNoDisponibleException)
    }
}
