package com.negociadororq.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Estrategia 1: Intentar cargar credenciales de servicio
        SecurityProperties.ServiceCredential serviceCredential = securityProperties.getServiceCredentials()
            .get(username);

        if (serviceCredential != null) {
            String scope = "SCOPE_" + username;
            log.fine("Autenticando servicio: " + username);
            return User.builder()
                .username(username)
                .password(serviceCredential.getPassword())
                .authorities(Collections.singleton(new SimpleGrantedAuthority(scope)))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
        }

        // Estrategia 2: Intentar cargar usuario de la base de datos
        log.fine("Usuario no encontrado en credenciales de servicio, buscando en BD: " + username);
        return databaseUserDetailsService.loadUserByUsername(username);
    }
}
