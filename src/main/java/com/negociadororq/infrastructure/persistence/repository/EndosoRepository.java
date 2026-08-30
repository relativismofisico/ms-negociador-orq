package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.EndosoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data MongoDB de {@link EndosoDocument}.
 */
@Repository
public interface EndosoRepository extends MongoRepository<EndosoDocument, String> {

    /**
     * @param negociacionId id de la negociación
     * @param numeroFactura número de la factura endosada
     * @return true si ya existe un endoso que cubra esa factura
     */
    boolean existsByNegociacionIdAndNumerosFacturaContains(String negociacionId, String numeroFactura);

    /**
     * @param negociacionId id de la negociación
     * @param numeroFactura número de la factura endosada
     * @return el endoso que cubre esa factura, si existe
     */
    Optional<EndosoDocument> findByNegociacionIdAndNumerosFacturaContains(String negociacionId, String numeroFactura);
}

