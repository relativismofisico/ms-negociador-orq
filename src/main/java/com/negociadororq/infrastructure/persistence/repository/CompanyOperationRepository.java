package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.CompanyOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyOperationRepository extends JpaRepository<CompanyOperationEntity, Long> {
    Optional<CompanyOperationEntity> findByCompanyIdAndOperationTypeAndEnabledTrue(
            Long companyId, String operationType);
}
