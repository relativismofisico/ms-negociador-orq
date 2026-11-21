package com.negociadororq.application.port.output;

/**
 * Fase 28d (A4): "cancelar si no procesada" en ms-liquidador-neg ({@code POST /internal/liquidaciones/cancelacion}),
 * idempotente por negociación y CUFE. El liquidador la decide sobre la misma marca que su liquidación y su fallida.
 */
public interface CancelacionLiquidacionOutputPort {

    /** Lo que decidió el liquidador para la solicitud. */
    enum Resultado {
        /** Se liquidó: se cierra sin revertir. */
        LIQUIDADA,
        /** Falló (con fallida publicada o no): se revierte una vez. */
        FALLIDA,
        /** Se canceló ahora o antes: se revierte una vez y va la plantilla A. */
        CANCELADA
    }

    /**
     * @param negociacionId negociación
     * @param cufe          CUFE de la factura
     * @return lo decidido
     * @throws com.negociadororq.application.exception.ServicioDependienteNoDisponibleException liquidador caído,
     *         timeout, 5xx o sin configurar: se reintenta en el siguiente ciclo
     */
    Resultado cancelar(String negociacionId, String cufe);
}
