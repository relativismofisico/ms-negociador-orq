package com.negociadororq.infrastructure.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
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
 *   service-credentials:
 *     ms-liquidador-neg:
 *       username: ${LIQUIDADOR_SERVICE_USERNAME}
 *       password: ${LIQUIDADOR_SERVICE_PASSWORD_HASH}
 *   roles:
 *     negociacion:
 *       - ADMINISTRADOR
 *       - EMPRESA
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security")
public class SecurityProperties implements InitializingBean {

    /** Hash bcrypt: prefijo $2a$, $2b$ o $2y$, coste de 04 a 31 y 53 caracteres de sal + hash. */
    private static final Pattern HASH_BCRYPT =
            Pattern.compile("^\\$2[aby]\\$(0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

    /** Placeholder que el binder no pudo resolver; el grupo 1 es el nombre de la variable. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)");

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
     * Busca la cuenta de servicio cuyo usuario configurado coincide con el recibido. La clave del
     * mapa solo identifica la cuenta en el YAML; el usuario llega por entorno y puede diferir.
     *
     * @param username usuario recibido en Basic Auth
     * @return la cuenta, o {@code null} si ninguna tiene ese usuario
     */
    public ServiceCredential buscarPorUsuario(String username) {
        return serviceCredentials.values().stream()
                .filter(credential -> credential.getUsername() != null && credential.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    /**
     * Valida al arrancar cada cuenta de servicio: usuario no vacío y contraseña con formato de hash
     * bcrypt. El mensaje de error nombra la propiedad o la variable que falta, nunca su valor.
     */
    @Override
    public void afterPropertiesSet() {
        serviceCredentials.forEach((cuenta, credential) -> {
            String prefijo = "security.service-credentials." + cuenta;
            exigirDefinida(prefijo + ".username", credential.getUsername());
            exigirDefinida(prefijo + ".password", credential.getPassword());
            if (credential.getUsername().isBlank()) {
                throw new IllegalStateException(prefijo + ".username es obligatorio y no puede estar vacío.");
            }
            if (!HASH_BCRYPT.matcher(credential.getPassword()).matches()) {
                throw new IllegalStateException(prefijo
                        + ".password debe ser un hash bcrypt ($2a$, $2b$ o $2y$, coste 04-31, 60 caracteres).");
            }
        });
    }

    /**
     * El binder deja literal un placeholder sin resolver ({@code ${VARIABLE}}): se informa el nombre de
     * la variable que falta, que no es un valor sensible.
     */
    private static void exigirDefinida(String propiedad, String valor) {
        if (valor == null) {
            throw new IllegalStateException(propiedad + " es obligatorio.");
        }
        Matcher placeholder = PLACEHOLDER.matcher(valor);
        if (placeholder.find()) {
            throw new IllegalStateException(propiedad + " no está definido: falta la variable de entorno "
                    + placeholder.group(1) + ".");
        }
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

