package com.negociadororq.application.port.output;

import java.util.List;

import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.ReferenciaFactura;

/**
 * Actualizaciones parciales (patch) sobre una factura ya persistida.
 */
public interface FacturaPatchOutputPort {

    /**
     * @param facturaId          id de la factura
     * @param estadoFirma        nuevo estado de firma
     * @param estadoNegociacion  nuevo estado de negociación
     */
    void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion);

    /**
     * @param facturaId   id de la factura
     * @param estadoFirma nuevo estado de firma
     * @param actores     actores resultantes de la negociación
     */
    void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores);

    /**
     * Fase 28d (C): por la referencia exacta de la factura de la negociación ({@code {cufe, rutProveedor,
     * rutPagador}}), nunca por el número.
     *
     * @param ref         factura
     * @param estadoFirma nuevo estado de firma
     * @throws com.negociadororq.application.exception.ConflictoException {@code FACTURA_NO_COINCIDE} si ninguna
     *         factura coincide
     */
    void actualizarEstadoFirmaPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma);

    /**
     * Actualiza estadoFirma y estadoNegociacion de la factura (sincronizados durante el ciclo de vida del endoso).
     *
     * @param ref               factura
     * @param estadoFirma       nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación
     * @throws com.negociadororq.application.exception.ConflictoException {@code FACTURA_NO_COINCIDE} si ninguna
     *         factura coincide
     */
    void actualizarEstadosPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion);

    /**
     * Deja registrado en la factura a qué negociación pertenece, para poder sub-agrupar facturas liquidadas por
     * negociación.
     *
     * @param ref           factura
     * @param negociacionId negociación
     * @throws com.negociadororq.application.exception.ConflictoException {@code FACTURA_NO_COINCIDE} si ninguna
     *         factura coincide
     */
    void actualizarNegociacionIdPorCufe(ReferenciaFactura ref, String negociacionId);
}
