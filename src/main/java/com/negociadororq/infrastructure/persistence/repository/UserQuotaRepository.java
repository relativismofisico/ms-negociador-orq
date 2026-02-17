package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.UserQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, String> {

    Optional<UserQuota> findByUser_IdeUser(Long userId);
}