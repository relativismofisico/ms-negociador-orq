package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.Endoso;

/**
 * Puerto de persistencia de endosos.
 */
public interface EndosoOutputPort {

    /**
     * @param negociacionId id de la negociación
     * @param numeroFactura número de la factura endosada
     * @return true si ya existe un endoso para esa combinación
     */
    boolean existsByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);

    /**
     * @param negociacionId id de la negociación
     * @param numeroFactura número de la factura endosada
     * @return el endoso, si existe
     */
    Optional<Endoso> findByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);

    /**
     * @param endoso endoso a persistir
     * @return el endoso persistido
     */
    Endoso save(Endoso endoso);
}
