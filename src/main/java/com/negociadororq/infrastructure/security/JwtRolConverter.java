package com.negociadororq.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Convierte el claim 'rol' del JWT en un GrantedAuthority de Spring Security.
 *
 * Reglas corporativas:
 * - El claim se llama 'rol' (singular), no 'roles' ni 'authorities'.
 * - No se usa prefijo ROLE_. Los roles se comparan directamente (ej: 'ADMINISTRADOR').
 * - Un usuario tiene exactamente un rol por token.
 */
@Component
public class JwtRolConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String CLAIM_ROL = "rol";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        String rol = jwt.getClaimAsString(CLAIM_ROL);
        if (StringUtils.hasText(rol)) {
            // Sin prefijo ROLE_ — se usa hasAuthority() en @PreAuthorize
            authorities.add(new SimpleGrantedAuthority(rol));
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}