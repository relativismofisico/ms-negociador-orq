package com.negociadororq.application.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cupo y tasa que un fondeador indirecto asigna a un proveedor puntual dentro de una
 * relación de Factoring indirecto con un pagador.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOfferSupplierQuotaDto {

    private Long funderId;
    private Long payerId;
    private Long supplierId;
    private BigDecimal tasa;
    private BigDecimal cupoDisponible;
}
