package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.Negociacion;

/**
 * Puerto de persistencia de negociaciones.
 */
public interface NegociacionOutputPort {

    /**
     * @param id id de la negociación
     * @return la negociación, si existe
     */
    Optional<Negociacion> findById(String id);

    /**
     * @param negociacion negociación a persistir
     * @return la negociación persistida
     */
    Negociacion save(Negociacion negociacion);

    /**
     * @param numeroFactura número de una factura incluida en la negociación
     * @return la negociación que contiene esa factura, si existe
     */
    Optional<Negociacion> findByNumeroFactura(String numeroFactura);
}
