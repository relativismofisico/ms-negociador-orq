package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.FondeadorQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface FondeadorQuotaRepository extends JpaRepository<FondeadorQuota, Long> {
    Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId);
}
