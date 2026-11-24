package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link UserEntity}.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * @param username nombre de usuario
     * @return el usuario, si existe
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Fase 28d (P-09): usernames de los usuarios de la empresa en ese estado (2 = Inactivo).
     *
     * @param companyIde  empresa
     * @param stateUserId estado
     * @return usernames
     */
    @org.springframework.data.jpa.repository.Query("SELECT u.username FROM UserEntity u WHERE u.companyIde = :companyIde"
            + " AND u.stateUserId = :stateUserId")
    java.util.List<String> usernamesPorEmpresaYEstado(
            @org.springframework.data.repository.query.Param("companyIde") Long companyIde,
            @org.springframework.data.repository.query.Param("stateUserId") Long stateUserId);
}

