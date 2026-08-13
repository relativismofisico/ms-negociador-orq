package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.CompanySectionVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanySectionVerificationRepository extends JpaRepository<CompanySectionVerificationEntity, Long> {
    List<CompanySectionVerificationEntity> findByCompanyIdAndSectionKeyIn(Long companyId, List<String> sectionKeys);
}
