package com.negociadororq.application.service;

/**
 * Revierte el cupo consumido por una factura cuando su negociación se
 * rechaza o cancela.
 */
public interface ReversionCuposService {

    /**
     * @param numeroFactura número de la factura cuyo cupo se revierte
     * @param actorId       id/RUT del actor que origina la reversión
     * @param motivo        motivo de la reversión, para trazabilidad
     */
    void revertirPorFactura(String numeroFactura, String actorId, String motivo);
}

