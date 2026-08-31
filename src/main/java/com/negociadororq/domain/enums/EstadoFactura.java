package com.negociadororq.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Estado de una factura a lo largo de su ciclo de vida, desde que se carga hasta que
 * se liquida.
 */
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
