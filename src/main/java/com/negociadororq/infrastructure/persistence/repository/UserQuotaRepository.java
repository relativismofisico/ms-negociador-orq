package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.UserQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA de {@link UserQuotaEntity}.
 */
@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuotaEntity, Long> {

    /**
     * @param userId id del usuario
     * @return el cupo del usuario, si existe
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    Optional<UserQuotaEntity> findByUser_IdeUser(Long userId);
}

