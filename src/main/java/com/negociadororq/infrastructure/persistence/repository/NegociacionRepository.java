package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NegociacionRepository extends MongoRepository<NegociacionDocument, String> {
    @Query("{ 'facturas.numeroFactura' : ?0 }")
    Optional<NegociacionDocument> findByNumeroFactura(String numeroFactura);
}

