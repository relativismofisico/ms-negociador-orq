package com.negociadororq.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.oauth2.jwt.JwtException
import spock.lang.Specification
import spock.lang.Unroll

/**
 * ms-security-util#55: el 401 lleva un mensaje único; el código conserva la categoría (contrato de la API) y se decide
 * por el tipo de la causa, no por su texto. El motivo concreto no sale al cliente.
 */
class CustomAuthenticationEntryPointSpec extends Specification {

    static final String EXPIRADO = 'JWT expired at 2026-10-22T10:00:00Z. Current time: 2026-10-22T11:00:00Z'

    def entryPoint = new CustomAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules())

    @Unroll
    def "#caso: 401 #codigo con el mensaje único y sin el motivo"() {
        given:
        def request = new MockHttpServletRequest('GET', '/negociaciones')
        if (header) {
            request.addHeader('Authorization', header)
        }
        def response = new MockHttpServletResponse()

        when:
        entryPoint.commence(request, response, excepcion)

        then:
        response.status == 401
        def cuerpo = new ObjectMapper().readValue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map)
        cuerpo.codigo == codigo
        cuerpo.mensaje == CustomAuthenticationEntryPoint.MENSAJE_NO_AUTENTICADO
        !response.contentAsString.contains(motivo)

        where:
        caso             | header             | motivo                       | codigo           | excepcion
        'token expirado' | 'Bearer x.y.z'     | 'Current time'               | 'TOKEN_EXPIRADO' | new BadCredentialsException('Token expirado', new JwtException('Token expirado', new ExpiredJwtException(null, null, EXPIRADO)))
        'sin header'     | null               | 'Full authentication'        | 'TOKEN_AUSENTE'  | new InsufficientAuthenticationException('Full authentication is required to access this resource')
        'token inválido' | 'Bearer no.es.jwt' | 'Malformed protected header' | 'TOKEN_INVALIDO' | new BadCredentialsException('Malformed protected header JSON: Unable to read JSON value')
    }
}
