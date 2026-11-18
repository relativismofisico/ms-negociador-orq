package com.negociadororq.application.service;

/**
 * Revierte el cupo consumido por una factura cuando su negociación se
 * rechaza o cancela.
 */
public interface ReversionCuposService {

    /**
     * Fase 28d (C): la factura se identifica por la negociación y el CUFE (nunca por el número solo). Sin CUFE o sin
     * negociación lanza {@link IllegalArgumentException} ({@code [EVENTO-SIN-CUFE]}).
     *
     * @param negociacionId negociación
     * @param cufe          CUFE de la factura
     * @param numeroFactura número de la factura (trazabilidad)
     * @param actorId       id/RUT del actor que origina la reversión
     * @param motivo        motivo de la reversión, para trazabilidad
     */
    void revertirPorFactura(String negociacionId, String cufe, String numeroFactura, String actorId, String motivo);
}

