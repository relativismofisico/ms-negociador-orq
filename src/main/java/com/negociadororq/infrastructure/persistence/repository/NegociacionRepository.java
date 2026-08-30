package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data MongoDB de {@link NegociacionDocument}.
 */
@Repository
public interface NegociacionRepository extends MongoRepository<NegociacionDocument, String> {

    /**
     * @param numeroFactura número de una factura incluida en la negociación
     * @return la negociación que contiene esa factura, si existe
     */
    @Query("{ 'facturas.numeroFactura' : ?0 }")
    Optional<NegociacionDocument> findByNumeroFactura(String numeroFactura);
}

