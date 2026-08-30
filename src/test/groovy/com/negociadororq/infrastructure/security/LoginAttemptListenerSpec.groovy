package com.negociadororq.infrastructure.security

import com.negociadororq.application.service.LoginLockoutService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.core.Authentication
import spock.lang.Specification

class LoginAttemptListenerSpec extends Specification {

    def loginLockoutService = Mock(LoginLockoutService)

    def listener = new LoginAttemptListener(loginLockoutService)

    private Authentication tokenFor(String username) {
        new UsernamePasswordAuthenticationToken(username, "irrelevante")
    }

    def "onFailure registra el intento fallido para credenciales invalidas"() {
        given:
        def authentication = tokenFor("op-1")
        def event = new AuthenticationFailureBadCredentialsEvent(authentication, new BadCredentialsException("mala"))

        when:
        listener.onFailure(event)

        then:
        1 * loginLockoutService.registerFailure("op-1")
    }

    def "onFailure no registra un intento adicional cuando la cuenta ya estaba bloqueada"() {
        given:
        def authentication = tokenFor("op-1")
        def event = new AuthenticationFailureLockedEvent(authentication,
                new org.springframework.security.authentication.LockedException("bloqueada"))

        when:
        listener.onFailure(event)

        then:
        0 * loginLockoutService.registerFailure(_)
    }

    def "onSuccess resetea el contador de intentos fallidos"() {
        given:
        def authentication = tokenFor("op-1")
        def event = new AuthenticationSuccessEvent(authentication)

        when:
        listener.onSuccess(event)

        then:
        1 * loginLockoutService.reset("op-1")
    }
}
