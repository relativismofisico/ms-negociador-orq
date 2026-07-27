package com.negociadororq.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EstadoFactura {
    CARGADA,
    SIN_NEGOCIAR,
    NEGOCIADA,
    RECHAZADA,
    VENCIDA,
    BLOQUEADA,
    VENDIDA,
    LIQUIDADA
}
