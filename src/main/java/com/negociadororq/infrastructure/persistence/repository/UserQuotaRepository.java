package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.UserQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuotaEntity, Long> {

    Optional<UserQuotaEntity> findByUser_IdeUser(Long userId);
}

