package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpresaQuotaRepository extends JpaRepository<EmpresaQuotaEntity, Long> {
    Optional<EmpresaQuotaEntity> findByCompanyId(Long companyId);
}
