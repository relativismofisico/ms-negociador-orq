package com.negociadororq.domain.enums;

/**
 * Estado de un endoso de descuento a lo largo de su flujo de revisión y aprobación.
 */
public enum EstadoEndoso {
    PENDIENTE,
    EN_REVISION,
    ACEPTADO,
    RECHAZADO,
    PENDIENTE_OTP,
    /** Endoso agrupado (varias facturas) donde algunas ya se aprobaron y otras se rechazaron. */
    PARCIAL
}
