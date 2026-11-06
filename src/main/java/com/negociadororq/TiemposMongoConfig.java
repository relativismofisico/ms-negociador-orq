package com.negociadororq;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Tiempos del driver de MongoDB. {@code serverSelectionTimeout} es lo que espera una operación cuando NO hay ningún
 * servidor disponible (Mongo caído): con el valor por defecto del driver (30 s) un fallo de Mongo tardaba 30 s en
 * llegar como 503. Aquí se fija en {@value #SELECCION_SERVIDOR_MS} ms ({@code MONGO_SERVER_SELECTION_TIMEOUT_MS}) y el
 * {@code connectTimeout} en {@value #CONEXION_MS} ms ({@code MONGO_CONNECT_TIMEOUT_MS}). El timeout de lectura del
 * socket (consultas en curso) no se toca.
 *
 * <p>Compromiso: durante una elección de primario del replica set (suele completarse en menos de ~12 s) una operación
 * puede agotar la espera y responder un 503 breve. En qa/prod se puede subir con la variable de entorno.</p>
 *
 * <p>Se aplica después del customizer estándar de Spring Boot (precedencia más baja), así que manda aunque la URI de
 * conexión traiga otro valor.</p>
 */
@Configuration(proxyBeanMethods = false)
public class TiemposMongoConfig {

    /** Espera por defecto de selección de servidor, en milisegundos. */
    static final String SELECCION_SERVIDOR_MS = "10000";

    /** Espera por defecto para abrir una conexión, en milisegundos. */
    static final String CONEXION_MS = "5000";

    /**
     * @param seleccionServidorMs {@code MONGO_SERVER_SELECTION_TIMEOUT_MS}
     * @param conexionMs          {@code MONGO_CONNECT_TIMEOUT_MS}
     * @return el customizer de los tiempos
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    MongoClientSettingsBuilderCustomizer tiemposMongo(
            @Value("${MONGO_SERVER_SELECTION_TIMEOUT_MS:" + SELECCION_SERVIDOR_MS + "}") final long seleccionServidorMs,
            @Value("${MONGO_CONNECT_TIMEOUT_MS:" + CONEXION_MS + "}") final int conexionMs) {
        return builder -> builder
                .applyToClusterSettings(c -> c.serverSelectionTimeout(seleccionServidorMs, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(s -> s.connectTimeout(conexionMs, TimeUnit.MILLISECONDS));
    }
}
