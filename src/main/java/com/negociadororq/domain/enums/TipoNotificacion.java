package com.negociadororq.domain.enums;

/**
 * Tipo de evento de negocio que dispara el envío de una notificación por email.
 */
public enum TipoNotificacion {
    NEGOCIACION_CREADA,
    ENDOSO_CREADO,
    ENDOSO_FIRMADO,
    OTP,
    LIQUIDACION_FALLIDA,
    FIRMA_FALLIDA,
    /** Fase 28d (A2): plantilla A, la operación se revirtió con éxito (los cupos quedaron como antes). */
    OPERACION_REVERTIDA,
    /** Fase 28d (A2): plantilla B (2.8), la reversión falló y la operación está en revisión manual. */
    OPERACION_EN_REVISION
}
