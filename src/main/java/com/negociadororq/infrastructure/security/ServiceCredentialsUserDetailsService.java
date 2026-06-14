package com.negociadororq.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ServiceCredentialsUserDetailsService implements UserDetailsService {

    private final SecurityProperties securityProperties;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityProperties.ServiceCredential credential = securityProperties.getServiceCredentials()
            .get(username);

        if (credential == null) {
            throw new UsernameNotFoundException("Service account not found: " + username);
        }

        List<SimpleGrantedAuthority> authorities = credential.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // Asignar autoridad con scope del servicio: ms-liquidador-neg -> SCOPE_ms-liquidador-neg
        //String scope = "SCOPE_" + username;
        return User.builder()
            .username(username)
            .password(credential.getPassword())
            .authorities((authorities))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build();
    }
}
