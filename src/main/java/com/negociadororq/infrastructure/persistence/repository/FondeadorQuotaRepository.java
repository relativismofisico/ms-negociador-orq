package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FondeadorQuotaRepository extends JpaRepository<FondeadorQuotaEntity, Long> {
    Optional<FondeadorQuotaEntity> findByFunderIdAndPayerId(Long funderId, Long payerId);
    Optional<FondeadorQuotaEntity> findByFunderIdAndSupplierId(Long funderId, Long supplierId);
}

