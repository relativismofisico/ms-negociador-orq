package com.negociadororq.infrastructure.security;

import java.util.List;
import java.util.stream.Collectors;

import com.negociadororq.application.service.LoginLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Servicio de detalles de usuario compuesto que intenta cargar credenciales en este orden:
 * 1. Credenciales de servicios (ms-liquidador-neg, etc.)
 * 2. Usuarios de la base de datos (operarios)
 *
 * Esto permite que tanto servicios como usuarios se autentiquen vía Basic Auth.
 */
@Service
@RequiredArgsConstructor
@Log
public class CompositeUserDetailsService implements UserDetailsService {

    private final SecurityProperties securityProperties;
    private final DatabaseUserDetailsService databaseUserDetailsService;
    private final LoginLockoutService loginLockoutService;

    @Override
    public UserDetails loadUserByUsername(String username) {
        // Estrategia 1: Intentar cargar credenciales de servicio
        SecurityProperties.ServiceCredential serviceCredential = securityProperties.buscarPorUsuario(username);

        if (serviceCredential != null) {
            log.fine("Autenticando servicio: " + username);

            List<SimpleGrantedAuthority> authorities = serviceCredential.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            return User.builder()
                    .username(username)
                    .password(serviceCredential.getPassword())
                    .authorities(authorities)
                    .accountExpired(false)
                    .accountLocked(loginLockoutService.isLocked(username))
                    .credentialsExpired(false)
                    .disabled(false)
                    .build();
        }

        // Estrategia 2: Intentar cargar usuario de la base de datos
        log.fine("Usuario no encontrado en credenciales de servicio, buscando en BD: " + username);
        return databaseUserDetailsService.loadUserByUsername(username);
    }
}
