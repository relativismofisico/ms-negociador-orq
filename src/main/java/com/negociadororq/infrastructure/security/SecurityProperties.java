package com.negociadororq.infrastructure.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private Map<String, ServiceCredential> serviceCredentials = new HashMap<>();

    /**
     * @param grupo grupo de roles configurado bajo {@code security.roles}
     * @return los roles autorizados para ese grupo, o una lista vacía si no está configurado
     */
    public List<String> getRolesForGroup(String grupo) {
        return roles.getOrDefault(grupo, Collections.emptyList());
    }

    /**
     * Configuración del JWT: la clave secreta de firma, en Base64.
     */
    @Getter
    @Setter
    public static class JwtProperties {
        private String secret;
    }

    /**
     * Credenciales Basic Auth de un servicio de la plataforma, con sus roles asociados.
     */
    @Getter
    @Setter
    public static class ServiceCredential {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();
    }
}

