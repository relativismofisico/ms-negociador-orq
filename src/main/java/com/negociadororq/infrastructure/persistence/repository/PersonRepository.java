package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.PersonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link PersonEntity}.
 */
public interface PersonRepository extends JpaRepository<PersonEntity, Long> {
}
