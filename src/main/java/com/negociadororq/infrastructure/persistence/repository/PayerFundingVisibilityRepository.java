package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.PayerFundingVisibilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link PayerFundingVisibilityEntity}.
 */
public interface PayerFundingVisibilityRepository extends JpaRepository<PayerFundingVisibilityEntity, Long> {

    /**
     * @param companyId id de la empresa pagadora
     * @return la visibilidad, si existe y está habilitada
     */
    Optional<PayerFundingVisibilityEntity> findByCompanyIdAndHabilitadoTrue(Long companyId);
}
