package com.negociadororq.application.port.output;

/**
 * Reclamo único de la reversión de cupos de una factura dentro de una negociación (tabla
 * {@code reversion_cupos_factura}, migración V2).
 */
public interface ReversionCuposReclamoOutputPort {

    /**
     * Inserta el reclamo en la transacción actual. Con otro reclamo igual sin confirmar, espera a que esa
     * transacción termine: si confirma, este devuelve {@code false}; si se revierte, este gana.
     *
     * @param numeroFactura número de la factura
     * @param negociacionId id de la negociación
     * @return {@code true} si este es el primer reclamo; {@code false} si la reversión ya estaba reclamada
     */
    boolean reclamar(String numeroFactura, String negociacionId);
}
