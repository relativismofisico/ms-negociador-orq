package com.negociadororq.web.exception

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import spock.lang.Specification

/**
 * Una ruta inexistente responde 404 con un mensaje fijo: sin el mensaje de la excepción ("No static resource ...") y
 * sin repetir la ruta pedida en el mensaje.
 */
class RutaInexistenteSpec extends Specification {

    static final String RUTA = '/ruta-inexistente-karate'
    static final String MENSAJE = 'El recurso solicitado no existe.'

    def handler = new GlobalExceptionHandler()

    /** Algún texto del cuerpo, salvo el campo path, repite la ruta pedida. */
    private static boolean repiteLaRuta(Map cuerpo) {
        for (valor in cuerpo.values()) {
            if (valor instanceof String && valor != RUTA && valor.contains(RUTA)) {
                return true
            }
        }
        false
    }

    def "#excepcion.class.simpleName responde 404 con mensaje fijo"() {
        when:
        def respuesta = handler.handleRutaInexistente(excepcion, new MockHttpServletRequest('GET', RUTA))
        def json = new ObjectMapper().findAndRegisterModules().writeValueAsString(respuesta.body)
        Map cuerpo = new ObjectMapper().readValue(json, Map)

        then:
        respuesta.statusCode.value() == 404
        cuerpo.values().contains(MENSAJE)
        !json.contains('No static resource')
        !json.contains('No endpoint')
        !repiteLaRuta(cuerpo)

        where:
        excepcion << [new NoResourceFoundException(HttpMethod.GET, RUTA),
                      new NoHandlerFoundException('GET', RUTA, new HttpHeaders())]
    }
}
