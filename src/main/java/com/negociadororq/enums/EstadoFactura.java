package com.negociadororq.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Clase enum de estados facturas
 *
 * @author John Redondo
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
    VENDIDA
}
