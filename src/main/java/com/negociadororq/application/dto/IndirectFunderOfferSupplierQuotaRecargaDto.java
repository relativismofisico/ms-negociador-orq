package com.negociadororq.application.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Recarga puntual del cupo disponible que un fondeador indirecto le asignó a un
 * proveedor sobre una oferta de Factoring indirecto ya existente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOfferSupplierQuotaRecargaDto {

    private Long offerId;
    private Long supplierId;
    private BigDecimal cupoDisponible;
}
