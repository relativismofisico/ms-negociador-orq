package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA de {@link EmpresaQuotaEntity}.
 */
@Repository
public interface EmpresaQuotaRepository extends JpaRepository<EmpresaQuotaEntity, Long> {

    /**
     * @param companyId id de la empresa
     * @return el cupo de la empresa, si existe
     */
    Optional<EmpresaQuotaEntity> findByCompanyId(Long companyId);
}
