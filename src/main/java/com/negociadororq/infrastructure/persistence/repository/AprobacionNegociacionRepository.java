package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.AprobacionNegociacionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Spring Data MongoDB de {@link AprobacionNegociacionDocument}.
 */
public interface AprobacionNegociacionRepository extends MongoRepository<AprobacionNegociacionDocument, String> {

    /**
     * @param negociacionId id de la negociación
     * @return las aprobaciones registradas para esa negociación
     */
    List<AprobacionNegociacionDocument> findByNegociacionId(String negociacionId);
}
