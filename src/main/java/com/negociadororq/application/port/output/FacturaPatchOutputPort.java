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
     * Fase 28d (A5): como {@link #actualizarEstadosPorCufe}, pero una factura VENCIDA no se toca.
     *
     * @param ref               factura (CUFE y RUT de la negociación)
     * @param estadoFirma       nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación
     * @return {@code true} si se actualizó; {@code false} si la factura ya estaba VENCIDA
     */
    boolean actualizarEstadosSiNoVencida(ReferenciaFactura ref, EstadoFirma estadoFirma,
                                         EstadoNegociacion estadoNegociacion);

    /**
     * Fase 28d (A5): firma final y marca de la solicitud, condicionadas a que la factura no esté VENCIDA ni en otro
     * estado terminal (compare-and-set). Va dentro de la transacción Mongo que escribe el evento.
     *
     * @param ref        factura (CUFE y RUT de la negociación)
     * @param firmaFinal CESION o DESCUENTO
     * @param idEvento   id del evento {@code liquidacion.solicitada}
     * @return {@code true} si ganó
     */
    boolean marcarFirmaFinalConSolicitud(ReferenciaFactura ref, EstadoFirma firmaFinal, String idEvento);

    /**
     * Fase 28d (A5): el job marca VENCIDA si la factura sigue como la leyó (compare-and-set).
     *
     * @param facturaId          id
     * @param estadoLeido        estado de negociación leído
     * @param firmaLeida         firma leída
     * @param reversionPendiente si hay cupos que devolver
     * @return {@code true} si la marcó
     */
    boolean marcarVencidaSiCoincide(String facturaId, String estadoLeido, String firmaLeida,
                                    boolean reversionPendiente);

    /**
     * @param facturaId Fase 28d (A5): la reversión del vencimiento terminó
     */
    void quitarReversionPendiente(String facturaId);

    /**
     * @param ref Fase 28d (A5): el seguimiento de la solicitud ya consta
     */
    void quitarSeguimientoLiquidacionPendiente(ReferenciaFactura ref);

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
