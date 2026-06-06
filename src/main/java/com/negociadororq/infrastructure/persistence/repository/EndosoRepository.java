package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.EndosoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EndosoRepository extends MongoRepository<EndosoDocument, String> {

    boolean existsByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);

    Optional<EndosoDocument> findByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);
}

