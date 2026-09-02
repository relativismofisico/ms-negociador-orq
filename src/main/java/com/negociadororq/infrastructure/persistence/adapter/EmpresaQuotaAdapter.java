package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.EmpresaQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        try {
            return toDomain(repository.saveAndFlush(toEntity(quota)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "Conflicto de optimistic locking al guardar EmpresaQuota id=" + quota.getId(), e);
        }
    }

    private EmpresaQuota toDomain(EmpresaQuotaEntity entity) {
        return EmpresaQuota.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .version(entity.getVersion())
                .build();
    }

    private EmpresaQuotaEntity toEntity(EmpresaQuota quota) {
        return EmpresaQuotaEntity.builder()
                .id(quota.getId())
                .companyId(quota.getCompanyId())
                .quota(quota.getQuota())
                .currentQuota(quota.getCurrentQuota())
                .version(quota.getVersion())
                .build();
    }
}
