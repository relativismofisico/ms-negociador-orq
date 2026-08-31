package com.negociadororq.domain.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cupo total asignado a una empresa y cuánto de ese cupo tiene consumido actualmente.
 */
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
    private Long version;
}
