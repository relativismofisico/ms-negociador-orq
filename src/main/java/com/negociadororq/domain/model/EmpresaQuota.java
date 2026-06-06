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
public class EmpresaQuota {

    private Long id;
    private Long companyId;
    private BigDecimal quota;
    private BigDecimal currentQuota;
}
