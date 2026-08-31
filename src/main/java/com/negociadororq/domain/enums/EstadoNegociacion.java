package com.negociadororq.domain.enums;

/**
 * Estado de una negociación de Factoring/Confirming a lo largo de su flujo de
 * aprobación, endoso y liquidación.
 */
public enum EstadoNegociacion {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    ENDOSADA,
    LIQUIDADA,
    EN_APROBACION,
    PARCIAL
}
