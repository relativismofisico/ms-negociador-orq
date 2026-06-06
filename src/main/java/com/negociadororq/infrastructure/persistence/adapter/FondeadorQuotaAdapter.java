package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.FondeadorQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FondeadorQuotaAdapter implements FondeadorQuotaOutputPort {

    private final FondeadorQuotaRepository repository;

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId) {
        return repository.findByFunderIdAndPayerId(funderId, payerId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId) {
        return repository.findByFunderIdAndSupplierId(funderId, supplierId).map(this::toDomain);
    }

    @Override
    public FondeadorQuota save(FondeadorQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    private FondeadorQuota toDomain(FondeadorQuotaEntity entity) {
        return FondeadorQuota.builder()
                .id(entity.getId())
                .funderId(entity.getFunderId())
                .supplierId(entity.getSupplierId())
                .payerId(entity.getPayerId())
                .payerQuota(entity.getPayerQuota())
                .supplierQuota(entity.getSupplierQuota())
                .payerCurrentQuota(entity.getPayerCurrentQuota())
                .supplierCurrentQuota(entity.getSupplierCurrentQuota())
                .build();
    }

    private FondeadorQuotaEntity toEntity(FondeadorQuota quota) {
        return FondeadorQuotaEntity.builder()
                .id(quota.getId())
                .funderId(quota.getFunderId())
                .supplierId(quota.getSupplierId())
                .payerId(quota.getPayerId())
                .payerQuota(quota.getPayerQuota())
                .supplierQuota(quota.getSupplierQuota())
                .payerCurrentQuota(quota.getPayerCurrentQuota())
                .supplierCurrentQuota(quota.getSupplierCurrentQuota())
                .build();
    }
}
