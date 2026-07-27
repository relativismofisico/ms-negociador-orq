package com.negociadororq.domain.enums;

public enum EstadoEndoso {
    PENDIENTE,
    EN_REVISION,
    ACEPTADO,
    RECHAZADO,
    PENDIENTE_OTP,
    /** Endoso agrupado (varias facturas) donde algunas ya se aprobaron y otras se rechazaron. */
    PARCIAL
}
