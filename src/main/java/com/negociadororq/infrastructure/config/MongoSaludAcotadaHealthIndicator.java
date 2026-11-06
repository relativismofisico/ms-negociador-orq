package com.negociadororq.infrastructure.config;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Component;

/**
 * Health de MongoDB con tiempo acotado. Reemplaza al indicador {@code mongo} de Spring Boot (mismo nombre de bean),
 * que con Mongo caído esperaba el serverSelectionTimeout completo del driver (30 s) antes de dar DOWN.
 *
 * <p>El {@code ping} corre en un hilo propio y se espera como máximo {@link #TIMEOUT_PING_MS} ms. Si el ping anterior
 * sigue sin respuesta, se informa DOWN sin lanzar otro (nunca se acumulan hilos colgados). No cambia los timeouts de
 * las consultas reales. Liveness no incluye este indicador; readiness sí, si su grupo lista {@code mongo}.</p>
 */
@Component("mongoHealthIndicator")
public class MongoSaludAcotadaHealthIndicator extends AbstractHealthIndicator implements DisposableBean {

    /** Milisegundos máximos de espera del ping. */
    static final long TIMEOUT_PING_MS = 2000;

    private static final String MOTIVO = "motivo";
    private static final String PING = "{ ping: 1 }";

    private final MongoOperations mongo;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(tarea -> {
        final Thread hilo = new Thread(tarea, "health-mongo");
        hilo.setDaemon(true);
        return hilo;
    });
    private Future<Boolean> pingEnCurso;

    /**
     * @param mongo plantilla de MongoDB de la aplicación
     */
    public MongoSaludAcotadaHealthIndicator(final MongoOperations mongo) {
        super("MongoDB no disponible");
        this.mongo = mongo;
    }

    @Override
    protected synchronized void doHealthCheck(final Health.Builder builder) throws Exception {
        if (pingEnCurso != null && !pingEnCurso.isDone()) {
            builder.down().withDetail(MOTIVO, "ping anterior sin respuesta");
            return;
        }
        pingEnCurso = ejecutor.submit(() -> {
            mongo.executeCommand(PING);
            return Boolean.TRUE;
        });
        try {
            pingEnCurso.get(TIMEOUT_PING_MS, TimeUnit.MILLISECONDS);
            builder.up();
        } catch (TimeoutException e) {
            builder.down().withDetail(MOTIVO, "sin respuesta en " + TIMEOUT_PING_MS + " ms");
        } catch (ExecutionException e) {
            builder.down().withDetail(MOTIVO, e.getCause().getClass().getSimpleName());
        }
    }

    @Override
    public void destroy() {
        ejecutor.shutdownNow();
    }
}
