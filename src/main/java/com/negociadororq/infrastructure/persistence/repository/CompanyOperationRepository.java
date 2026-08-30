package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.CompanyOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link CompanyOperationEntity}.
 */
public interface CompanyOperationRepository extends JpaRepository<CompanyOperationEntity, Long> {

    /**
     * @param companyId     id de la empresa
     * @param operationType tipo de operación (FACTORING, CONFIRMING, etc.)
     * @return la habilitación, si existe y está activa
     */
    Optional<CompanyOperationEntity> findByCompanyIdAndOperationTypeAndEnabledTrue(
            Long companyId, String operationType);
}
