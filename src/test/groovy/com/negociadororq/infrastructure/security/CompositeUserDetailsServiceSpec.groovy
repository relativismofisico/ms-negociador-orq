package com.negociadororq.infrastructure.security

import com.negociadororq.application.service.LoginLockoutService
import org.springframework.security.core.userdetails.UserDetails
import spock.lang.Specification

class CompositeUserDetailsServiceSpec extends Specification {

    def securityProperties = new SecurityProperties()
    def databaseUserDetailsService = Mock(DatabaseUserDetailsService)
    def loginLockoutService = Mock(LoginLockoutService)

    def service = new CompositeUserDetailsService(securityProperties, databaseUserDetailsService, loginLockoutService)

    def "credencial de servicio no bloqueada: isAccountNonLocked() es true"() {
        given:
        def credential = new SecurityProperties.ServiceCredential(
                username: "ms-liquidador-neg", password: "hash", roles: ["OPERARIO"])
        securityProperties.serviceCredentials = [ "ms-liquidador-neg": credential ]
        loginLockoutService.isLocked("ms-liquidador-neg") >> false

        when:
        UserDetails result = service.loadUserByUsername("ms-liquidador-neg")

        then:
        result.isAccountNonLocked()
        0 * databaseUserDetailsService.loadUserByUsername(_)
    }

    def "credencial de servicio bloqueada por LoginLockoutService: isAccountNonLocked() es false"() {
        given:
        def credential = new SecurityProperties.ServiceCredential(
                username: "ms-liquidador-neg", password: "hash", roles: ["OPERARIO"])
        securityProperties.serviceCredentials = [ "ms-liquidador-neg": credential ]
        loginLockoutService.isLocked("ms-liquidador-neg") >> true

        when:
        UserDetails result = service.loadUserByUsername("ms-liquidador-neg")

        then:
        !result.isAccountNonLocked()
    }

    def "username que no es credencial de servicio: delega en DatabaseUserDetailsService"() {
        given:
        securityProperties.serviceCredentials = [:]
        def dbUser = org.springframework.security.core.userdetails.User.builder()
                .username("operario1").password("enc").authorities("OPERARIO").build()
        databaseUserDetailsService.loadUserByUsername("operario1") >> dbUser

        when:
        def result = service.loadUserByUsername("operario1")

        then:
        result == dbUser
        0 * loginLockoutService.isLocked(_)
    }
}
