package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data MongoDB de {@link NegociacionDocument}.
 */
@Repository
public interface NegociacionRepository extends MongoRepository<NegociacionDocument, String> {
}
