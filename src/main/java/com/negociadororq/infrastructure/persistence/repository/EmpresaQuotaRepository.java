package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.EmpresaQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmpresaQuotaRepository extends JpaRepository<EmpresaQuota, Long> {
    Optional<EmpresaQuota> findByCompanyId(Long rutEmpresa);
}
