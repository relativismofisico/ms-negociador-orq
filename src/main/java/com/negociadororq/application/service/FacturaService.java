package com.negociadororq.application.service;

import java.util.List;

import com.negociadororq.domain.model.Factura;

/**
 * Consultas y actualizaciones sobre facturas usadas por el flujo de
 * negociación, endoso y firma.
 */
public interface FacturaService {

    /**
     * Actualiza el estado de firma de una factura según su número.
     *
     * @param numeroFactura número único de la factura
     * @param nuevoEstado nuevo estado de firma
     */
    void actualizarEstadoFirma(String numeroFactura, String nuevoEstado);

    /**
     * Actualiza el estado de firma de varias facturas en lote.
     *
     * @param numerosFacturas lista de números de factura
     * @param nuevoEstado nuevo estado de firma
     */
    void actualizarEstadoFirmaMasivo(List<String> numerosFacturas, String nuevoEstado);

    /**
     * Consulta una factura por su número.
     */
    Factura obtenerPorNumero(String numeroFactura);

    /**
     * Verifica si existen facturas de un pagador con los números dados.
     */
    boolean existenFacturasDePagador(List<String> numerosFacturas, String rutPagador);
}


