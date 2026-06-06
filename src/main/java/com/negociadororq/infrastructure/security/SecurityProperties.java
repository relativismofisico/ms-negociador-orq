package com.negociadororq.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Propiedades de seguridad leídas desde application.yml bajo el prefijo 'security'.
 *
 * Permite modificar roles autorizados por grupo sin recompilar el microservicio.
 *
 * Estructura YAML:
 * security:
 *   jwt:
 *     secret: <clave-base64>
 *   roles:
 *     negociacion:
 *       - ADMINISTRADOR
 *       - EMPRESA
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private JwtProperties jwt = new JwtProperties();
    private Map<String, List<String>> roles = new HashMap<>();

    public List<String> getRolesForGroup(String grupo) {
        return roles.getOrDefault(grupo, Collections.emptyList());
    }

    @Getter
    @Setter
    public static class JwtProperties {
        private String secret;
    }
}