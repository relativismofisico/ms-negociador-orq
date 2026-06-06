package com.negociadororq.domain.model;

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
public class FondeadorQuota {

    private Long id;
    private Long funderId;
    private Long supplierId;
    private Long payerId;
    private BigDecimal payerQuota;
    private BigDecimal supplierQuota;
    private BigDecimal payerCurrentQuota;
    private BigDecimal supplierCurrentQuota;
}
