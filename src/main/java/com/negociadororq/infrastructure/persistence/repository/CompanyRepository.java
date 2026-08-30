package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link CompanyEntity}.
 */
public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {

    /**
     * @param nit NIT de la empresa
     * @return la empresa, si existe
     */
    Optional<CompanyEntity> findByNit(String nit);
}
