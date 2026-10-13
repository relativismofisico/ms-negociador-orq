package com.negociadororq.infrastructure.security

import com.factoring.commons.mdc.MdcConstants
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

import java.time.Instant

class JwtRolConverterMdcSpec extends Specification {

    JwtRolConverter converter = new JwtRolConverter()

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    private static Jwt jwt(Map claims) {
        Jwt.withTokenValue("token.firmado.test")
                .header("alg", "HS384")
                .subject("KARATE-NEG-OP")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims({ it.putAll(claims) })
                .build()
    }

    def "convert() exporta el claim emp al atributo de la request para el MDC (ms-commons-util)"() {
        given:
        def request = new MockHttpServletRequest()
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        def autenticacion = converter.convert(jwt([rol: "OPERARIO", emp: 7600]))

        then:
        autenticacion.name == "KARATE-NEG-OP"
        request.getAttribute(MdcConstants.COMPANY_ID_ATTR) == 7600
    }

    def "token sin emp (cuenta de servicio): el atributo queda vacío"() {
        given:
        def request = new MockHttpServletRequest()
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        converter.convert(jwt([rol: "ADMINISTRADOR"]))

        then:
        request.getAttribute(MdcConstants.COMPANY_ID_ATTR) == null
    }

    def "fuera de una request HTTP (sin RequestContextHolder) no falla"() {
        expect:
        converter.convert(jwt([rol: "OPERARIO", emp: 7600])) != null
    }
}
