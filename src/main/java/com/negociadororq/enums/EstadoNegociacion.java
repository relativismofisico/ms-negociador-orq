package com.negociadororq.enums;

public enum EstadoNegociacion {
    PENDIENTE,   // creada, esperando aprobaciones
    APROBADA,    // pagador + fondeador aceptaron
    RECHAZADA,   // alguien rechazó
    ENDOSADA,    // proveedor endosó y fue aceptado
    LIQUIDADA,   // enviada a ms-liquidador-ne
    EN_APROBACION,
    PARCIAL
}
