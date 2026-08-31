package com.negociadororq.infrastructure.security;

import com.negociadororq.application.service.LoginLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Escucha los eventos de autenticación que publica {@code ProviderManager}
 * (vía el {@link org.springframework.security.authentication.AuthenticationEventPublisher}
 * registrado en {@code SecurityConfig}) para alimentar {@link LoginLockoutService} —
 * cubre tanto Basic Auth de servicios como operarios de BD, ya que ambos
 * pasan por el mismo {@code DaoAuthenticationProvider}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptListener {

    private final LoginLockoutService loginLockoutService;

    /**
     * Registra el intento fallido, salvo que la cuenta ya estuviera
     * bloqueada (evita seguir sumando ruido sobre una cuenta que un
     * atacante insiste en golpear mientras está bloqueada).
     *
     * @param event evento de fallo de autenticación
     */
    @EventListener
    public void onFailure(final AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureLockedEvent) {
            return;
        }
        final String username = event.getAuthentication().getName();
        log.warn("[LoginAttemptListener][onFailure][ms-negociador-orq] Intento fallido para: {}", username);
        loginLockoutService.registerFailure(username);
    }

    /**
     * Resetea el contador de intentos fallidos tras una autenticación exitosa.
     *
     * @param event evento de autenticación exitosa
     */
    @EventListener
    public void onSuccess(final AuthenticationSuccessEvent event) {
        loginLockoutService.reset(event.getAuthentication().getName());
    }
}
