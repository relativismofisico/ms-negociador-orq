package com.negociadororq.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Valida al arrancar, antes de crear el contexto (y por tanto antes de abrir el pool de conexiones), que la
 * configuración obligatoria llega por entorno. El error nombra la VARIABLE de entorno que falta, está vacía o no
 * tiene el formato esperado; nunca su valor.
 *
 * <p>Se registra en {@code main} y no en {@code spring.factories}: los tests de slice reciben el datasource por
 * propiedades dinámicas que se añaden después de este evento.
 */
public final class ConfiguracionObligatoria implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Propiedades que deben llegar resueltas y no vacías. */
    static final List<String> REQUERIDAS = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "security.service-credentials.ms-liquidador-neg.username",
            "security.service-credentials.ms-liquidador-neg.password");

    /** Propiedades que, además, deben ser un hash bcrypt. */
    static final List<String> HASHES_BCRYPT = List.of("security.service-credentials.ms-liquidador-neg.password");

    /** Hash bcrypt: prefijo $2a$, $2b$ o $2y$, coste de 04 a 31 y 53 caracteres de sal + hash. */
    private static final Pattern HASH_BCRYPT =
            Pattern.compile("^\\$2[aby]\\$(0[4-9]|[12]\\d|3[01])\\$[./A-Za-z0-9]{53}$");

    /**
     * {@code ${NOMBRE}} o {@code ${NOMBRE:default}}: el grupo 1 es el nombre y el 2 es {@code ":"} si hay default.
     * Sin cuantificadores encadenados (sin riesgo de ReDoS): el default no hace falta leerlo.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(:?)");

    /** Fuente que Spring Boot antepone a las demás y que solo las envuelve. */
    private static final String FUENTE_ADJUNTA = "configurationProperties";

    @Override
    public void onApplicationEvent(final ApplicationEnvironmentPreparedEvent event) {
        validar(event.getEnvironment());
    }

    /**
     * Revisa todas las propiedades obligatorias y, si alguna falla, informa todas juntas.
     *
     * @param environment entorno ya preparado (perfiles y application*.yml cargados)
     * @throws IllegalStateException con los nombres de las variables que fallan, sin valores
     */
    static void validar(final ConfigurableEnvironment environment) {
        List<String> errores = new ArrayList<>();
        for (String propiedad : REQUERIDAS) {
            revisar(environment, propiedad, errores);
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("Configuración obligatoria incompleta: " + String.join("; ", errores)
                    + ". Revise el .env del servicio (ver README).");
        }
    }

    private static void revisar(final ConfigurableEnvironment environment, final String propiedad,
            final List<String> errores) {
        PropertySource<?> fuente = fuenteDe(environment, propiedad);
        if (fuente == null) {
            errores.add("la propiedad " + propiedad + " no está configurada");
            return;
        }
        Matcher placeholder = PLACEHOLDER.matcher(String.valueOf(fuente.getProperty(propiedad)));
        String variable = null;
        boolean faltaAlguna = false;
        while (placeholder.find()) {
            String nombre = placeholder.group(1);
            if (variable == null) {
                variable = nombre;
            }
            if (!placeholder.group(2).isEmpty()) {
                continue;
            }
            String valorVariable = environment.getProperty(nombre);
            if (valorVariable == null) {
                errores.add("falta la variable de entorno " + nombre);
                faltaAlguna = true;
            } else if (valorVariable.isBlank()) {
                errores.add("la variable de entorno " + nombre + " está vacía");
                faltaAlguna = true;
            }
        }
        if (faltaAlguna) {
            return;
        }
        String nombreVisible = nombreDe(fuente, propiedad);
        if (variable != null) {
            nombreVisible = "la variable de entorno " + variable;
        }
        String valor = environment.getProperty(propiedad);
        if (sinValor(valor)) {
            errores.add(nombreVisible + " está vacía");
        } else if (HASHES_BCRYPT.contains(propiedad) && !HASH_BCRYPT.matcher(valor).matches()) {
            errores.add(nombreVisible + " no es un hash bcrypt ($2a$, $2b$ o $2y$, coste 04-31, 60 caracteres)");
        }
    }

    private static PropertySource<?> fuenteDe(final ConfigurableEnvironment environment, final String propiedad) {
        for (PropertySource<?> fuente : environment.getPropertySources()) {
            if (!FUENTE_ADJUNTA.equals(fuente.getName()) && fuente.containsProperty(propiedad)) {
                return fuente;
            }
        }
        return null;
    }

    /** Sin placeholder el valor viene tal cual de su fuente: si es el entorno, se nombra la variable. */
    private static String nombreDe(final PropertySource<?> fuente, final String propiedad) {
        if (fuente instanceof SystemEnvironmentPropertySource) {
            return "la variable de entorno "
                    + propiedad.toUpperCase(Locale.ROOT).replace('.', '_').replace("-", "");
        }
        return "la propiedad " + propiedad;
    }

    private static boolean sinValor(final String valor) {
        return valor == null || valor.isBlank();
    }
}
