package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Referencia mínima a un fondeador asociado a una factura negociada (RUT y nombre).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FondeadorAsignacion {
    private String rutFondeador;
    private String nombreFondeador;
}

