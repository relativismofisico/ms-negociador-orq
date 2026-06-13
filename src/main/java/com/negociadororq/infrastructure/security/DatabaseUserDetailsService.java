package com.negociadororq.infrastructure.security;

import com.negociadororq.infrastructure.persistence.entity.UserEntity;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
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
 * UserDetailsService que consulta la tabla de usuarios de la base de datos.
 * Se usa para autenticación Basic Auth con operarios (usando rutOperador como username).
 */
@Service
@RequiredArgsConstructor
@Log
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        log.fine("Usuario autenticado desde BD: " + username);

        return User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(Collections.singleton(new SimpleGrantedAuthority("OPERARIO")))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(user.getStateUserId() != 1)
            .build();
    }
}
