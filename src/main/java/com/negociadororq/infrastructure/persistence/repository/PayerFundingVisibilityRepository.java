package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.PayerFundingVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayerFundingVisibilityRepository extends JpaRepository<PayerFundingVisibilityEntity, Long> {

    Optional<PayerFundingVisibilityEntity> findByCompanyIdAndHabilitadoTrue(Long companyId);
}
