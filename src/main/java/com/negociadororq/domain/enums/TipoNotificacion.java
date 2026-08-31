package com.negociadororq.domain.enums;

/**
 * Tipo de evento de negocio que dispara el envío de una notificación por email.
 */
public enum TipoNotificacion {
    NEGOCIACION_CREADA,
    ENDOSO_CREADO,
    ENDOSO_FIRMADO,
    OTP,
    LIQUIDACION_FALLIDA
}
