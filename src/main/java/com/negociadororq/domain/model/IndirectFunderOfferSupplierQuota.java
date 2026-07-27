package com.negociadororq.domain.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consumo independiente de un proveedor contra una {@link IndirectFunderOffer}:
 * cada proveedor habilitado de la pagadora tiene su propio contador de cupo,
 * creado de forma perezosa la primera vez que ese proveedor negocia.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOfferSupplierQuota {

    private Long id;
    private Long offerId;
    private Long supplierId;
    private BigDecimal rate;
    private BigDecimal quota;
    private BigDecimal currentQuota;
    private BigDecimal originalQuota;
}
