package com.negociadororq.application.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
