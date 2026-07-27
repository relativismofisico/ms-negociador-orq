package com.negociadororq.domain.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Oferta de cupo compartido de un fondeador hacia una empresa pagadora
 * ("factoring indirecto vía pagador"): el cupo no se reparte entre los
 * proveedores de la pagadora, cada uno dispone del cupo íntegro de forma
 * independiente (ver {@link IndirectFunderOfferSupplierQuota}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOffer {

    private Long id;
    private Long funderId;
    private Long payerId;
    private BigDecimal payerRate;
    private BigDecimal supplierQuotaTemplate;
    private String estado;
    private String origen;
    private Boolean aprobadoPorFondeador;
    private Boolean activo;
}
