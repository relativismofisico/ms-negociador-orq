package com.negociadororq.application.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Fase 28d (C): un evento que identifica una factura sin CUFE (o sin su negociación) nunca se ignora en silencio: se
 * rechaza con el log {@code [EVENTO-SIN-CUFE]} y suma al contador {@code eventos.sin.cufe}; el consumidor lo deja
 * fallido (reintentos y DLT), visible.
 */
public final class EventosSinCufe {

    private static final AtomicLong RECHAZADOS = new AtomicLong();
    private static final Logger LOG = Logger.getLogger(EventosSinCufe.class.getName());

    private EventosSinCufe() {
    }

    /**
     * @param evento        nombre del evento
     * @param negociacionId negociación (puede ser null)
     * @param numeroFactura número (puede ser null)
     * @return la excepción a lanzar
     */
    public static IllegalArgumentException rechazar(final String evento, final String negociacionId,
            final String numeroFactura) {
        RECHAZADOS.incrementAndGet();
        LOG.severe(() -> "[EVENTO-SIN-CUFE] " + evento + " negociación " + negociacionId + " factura " + numeroFactura
                + ": sin CUFE o sin negociación no se identifica la factura; el evento queda fallido.");
        return new IllegalArgumentException("[EVENTO-SIN-CUFE] " + evento + " sin CUFE o sin negociación");
    }

    /**
     * @return eventos rechazados por esta causa desde el arranque
     */
    public static long rechazados() {
        return RECHAZADOS.get();
    }
}
