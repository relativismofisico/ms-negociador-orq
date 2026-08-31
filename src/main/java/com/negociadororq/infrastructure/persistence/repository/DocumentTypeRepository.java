package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.DocumentTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link DocumentTypeEntity}.
 */
public interface DocumentTypeRepository extends JpaRepository<DocumentTypeEntity, Long> {
}
