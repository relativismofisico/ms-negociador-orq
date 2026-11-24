package com.negociadororq.infrastructure.security

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import spock.lang.Shared
import spock.lang.Specification

/**
 * Fase 28d (J-06, decisión de John): rotación sin cortes de la cuenta de servicio ms-liquidador-neg. Hash actual o
 * anterior; cuál coincidió sin valores; arranque con ANTERIOR inválida (el error nombra la variable) o sin ANTERIOR; un
 * usuario que no es cuenta de servicio sigue al proveedor de la base.
 */
class CuentaServicioAuthenticationProviderSpec extends Specification {

    @Shared
    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4)
    @Shared
    String hashActual = bcrypt.encode("clave-nueva")
    @Shared
    String hashAnterior = bcrypt.encode("clave-vieja")

    SecurityProperties propiedades(String anterior) {
        def p = new SecurityProperties()
        def c = new SecurityProperties.ServiceCredential()
        c.username = "svc-liquidador"
        c.password = hashActual
        c.passwordAnterior = anterior
        c.passwordAnteriorVariable = "LIQUIDADOR_SERVICE_PASSWORD_HASH_ANTERIOR"
        c.roles = ["OPERARIO"]
        p.serviceCredentials = ["ms-liquidador-neg": c]
        p
    }

    CuentaServicioAuthenticationProvider proveedor(String anterior) {
        new CuentaServicioAuthenticationProvider(propiedades(anterior), bcrypt)
    }

    def "contraseña actual: autentica (hash=actual); anterior durante la rotación: autentica (hash=anterior)"() {
        given:
        def p = proveedor(hashAnterior)

        when:
        def a = p.authenticate(new UsernamePasswordAuthenticationToken("svc-liquidador", "clave-nueva"))
        def b = p.authenticate(new UsernamePasswordAuthenticationToken("svc-liquidador", "clave-vieja"))

        then:
        a.authenticated
        a.authorities*.authority == ["OPERARIO"]
        b.authenticated
        p.autenticaciones("svc-liquidador", "actual") == 1
        p.autenticaciones("svc-liquidador", "anterior") == 1
    }

    def "contraseña incorrecta o la anterior sin rotación: credenciales inválidas"() {
        when:
        proveedor(hashAnterior).authenticate(new UsernamePasswordAuthenticationToken("svc-liquidador", "otra"))

        then:
        thrown(BadCredentialsException)

        when:
        proveedor("").authenticate(new UsernamePasswordAuthenticationToken("svc-liquidador", "clave-vieja"))

        then:
        thrown(BadCredentialsException)
    }

    def "un usuario que no es cuenta de servicio no lo atiende: sigue al proveedor de la base"() {
        expect:
        proveedor(hashAnterior).authenticate(new UsernamePasswordAuthenticationToken("operador-real", "x")) == null
    }

    def "arranque: sin ANTERIOR arranca; con ANTERIOR que no es bcrypt no arranca y el error nombra la variable, no el valor"() {
        when:
        propiedades("").afterPropertiesSet()
        propiedades(null).afterPropertiesSet()
        propiedades(hashAnterior).afterPropertiesSet()

        then:
        noExceptionThrown()

        when:
        propiedades("no-es-un-hash-secreto").afterPropertiesSet()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("LIQUIDADOR_SERVICE_PASSWORD_HASH_ANTERIOR")
        !e.message.contains("no-es-un-hash-secreto")
    }

    def "la alerta periódica corre con y sin rotación en curso"() {
        when:
        proveedor(hashAnterior).alertarHashAnteriorActivo()
        proveedor("").alertarHashAnteriorActivo()

        then:
        noExceptionThrown()
    }
}
