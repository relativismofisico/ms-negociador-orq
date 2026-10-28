package com.negociadororq.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * BD caída al comprobar el usuario del token: el decoder lanza ValidacionTokenNoDisponibleException y este filtro,
 * delante del BearerTokenAuthenticationFilter, responde 503 con mensaje fijo en vez del 401 que cerraba la sesión.
 */
class ValidacionTokenNoDisponibleFilterSpec extends Specification {

    def entryPoint = new CustomAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules())
    def filtro = new ValidacionTokenNoDisponibleFilter(entryPoint)

    def "BD caída al validar el token -> 503 SERVICIO_NO_DISPONIBLE con mensaje fijo, sin detalle interno"() {
        given:
        def request = new MockHttpServletRequest('GET', '/negociaciones')
        def response = new MockHttpServletResponse()
        FilterChain cadena = Mock()
        cadena.doFilter(_, _) >> {
            throw new ValidacionTokenNoDisponibleException(new CannotGetJdbcConnectionException('detalle-interno'))
        }

        when:
        filtro.doFilter(request, response, cadena)

        then:
        response.status == 503
        def cuerpo = new ObjectMapper().readValue(response.getContentAsString(StandardCharsets.UTF_8), Map)
        cuerpo.codigo == CustomAuthenticationEntryPoint.CODIGO_NO_DISPONIBLE
        cuerpo.mensaje == CustomAuthenticationEntryPoint.MENSAJE_NO_DISPONIBLE
        !response.contentAsString.contains('detalle-interno')
    }

    def "sin fallo de infraestructura el filtro no interviene: la cadena decide (200 o 401)"() {
        given:
        def request = new MockHttpServletRequest('GET', '/negociaciones')
        def response = new MockHttpServletResponse()
        FilterChain cadena = Mock()

        when:
        filtro.doFilter(request, response, cadena)

        then:
        1 * cadena.doFilter(request, response)
        response.status == 200
    }
}
