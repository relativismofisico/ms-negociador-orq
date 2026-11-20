package com.negociadororq.domain.model;

/** Fase 28d, opción A: las operaciones mixtas que se registran en {@code operacion_pendiente}. */
public enum TipoOperacionPendiente {
    /** Cupos (MySQL) y negociación + factura NEGOCIADA (Mongo). */
    CREAR_NEGOCIACION,
    /** Reversión de cupos y reclamo (MySQL) y estado en Mongo. */
    RECHAZO,
    /** Ídem, por endoso rechazado. */
    ENDOSO_RECHAZADO,
    /** Ídem, por liquidación fallida. */
    LIQUIDACION_FALLIDA,
    /** Ídem, por vencimiento de la factura. */
    VENCIMIENTO,
    /** Fase 28d (A4): solicitud de liquidación en seguimiento hasta su resultado (reenvíos y cancelación). */
    LIQUIDACION_PENDIENTE,
    /** Fase 28d (A4): reversión por una liquidación sin resultado cancelada en el liquidador (con la plantilla A). */
    LIQUIDACION_CANCELADA
}
