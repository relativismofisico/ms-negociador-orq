package com.negociadororq.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.factoring.commons.mdc.MdcConstants;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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
        // Trazabilidad (ms-commons-util): la empresa del token ya validado (claim emp) para el MDC de la request.
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos != null) {
            atributos.setAttribute(MdcConstants.COMPANY_ID_ATTR, jwt.getClaim("emp"), RequestAttributes.SCOPE_REQUEST);
        }

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        String rol = jwt.getClaimAsString(CLAIM_ROL);
        if (StringUtils.hasText(rol)) {
            // Sin prefijo ROLE_ — se usa hasAuthority() en @PreAuthorize
            authorities.add(new SimpleGrantedAuthority(rol.toUpperCase(Locale.ROOT)));
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}

