package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.Negociacion;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NegociacionRepository extends MongoRepository<Negociacion, String> {
    @Query("{ 'facturas.numeroFactura' : ?0 }")
    Optional<Negociacion> findByNumeroFactura(String numeroFactura);
}
