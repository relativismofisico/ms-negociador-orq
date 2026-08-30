package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.EmpresaQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link EmpresaQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class EmpresaQuotaAdapter implements EmpresaQuotaOutputPort {

    private final EmpresaQuotaRepository repository;

    @Override
    public Optional<EmpresaQuota> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(this::toDomain);
    }

    @Override
    public EmpresaQuota save(EmpresaQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    @Override
    public EmpresaQuota saveAndFlush(EmpresaQuota quota) {
        return toDomain(repository.saveAndFlush(toEntity(quota)));
    }

    private EmpresaQuota toDomain(EmpresaQuotaEntity entity) {
        return EmpresaQuota.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .build();
    }

    private EmpresaQuotaEntity toEntity(EmpresaQuota quota) {
        return EmpresaQuotaEntity.builder()
                .id(quota.getId())
                .companyId(quota.getCompanyId())
                .quota(quota.getQuota())
                .currentQuota(quota.getCurrentQuota())
                .build();
    }
}
