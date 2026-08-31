package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.CompanySectionVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link CompanySectionVerificationEntity}.
 */
public interface CompanySectionVerificationRepository extends JpaRepository<CompanySectionVerificationEntity, Long> {

    /**
     * @param companyId   id de la empresa
     * @param sectionKeys claves de sección a consultar
     * @return las verificaciones de esa empresa para esas secciones
     */
    List<CompanySectionVerificationEntity> findByCompanyIdAndSectionKeyIn(Long companyId, List<String> sectionKeys);
}
