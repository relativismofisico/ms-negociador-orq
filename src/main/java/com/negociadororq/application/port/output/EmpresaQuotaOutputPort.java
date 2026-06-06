package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.EmpresaQuota;

import java.util.Optional;

public interface EmpresaQuotaOutputPort {
    Optional<EmpresaQuota> findByCompanyId(Long companyId);
    EmpresaQuota save(EmpresaQuota quota);
    EmpresaQuota saveAndFlush(EmpresaQuota quota);
}
