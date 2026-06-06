package com.negociadororq.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Evaluador de expresiones SpEL para @PreAuthorize.
 *
 * Permite externalizar la lista de roles autorizados por grupo al application.yml,
 * eliminando roles hardcodeados en el código fuente.
 *
 * Uso en controllers:
 *   @PreAuthorize("@sec.tieneRol(authentication, 'negociacion')")
 *
 * Los grupos se configuran en application.yml:
 *   security.roles.negociacion: [ADMINISTRADOR, EMPRESA, OPERARIO]
 */
@Component("sec")
@RequiredArgsConstructor
public class SecurityEvaluator {

    private final SecurityProperties securityProperties;

    /**
     * Verifica si el usuario autenticado tiene alguno de los roles
     * configurados para el grupo dado.
     *
     * @param authentication contexto de autenticación de Spring Security
     * @param grupo          clave del grupo de roles en security.roles.*
     * @return true si el usuario tiene al menos un rol del grupo
     */
    public boolean tieneRol(Authentication authentication, String grupo) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        List<String> rolesPermitidos = securityProperties.getRolesForGroup(grupo);

        if (rolesPermitidos.isEmpty()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rolesPermitidos::contains);
    }
}
