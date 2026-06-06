package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.FondeadorQuota;

import java.util.Optional;

public interface FondeadorQuotaOutputPort {
    Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId);
    Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId);
    FondeadorQuota save(FondeadorQuota quota);
}
