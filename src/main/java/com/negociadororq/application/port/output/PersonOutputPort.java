package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.DatosPersona;

/**
 * Puerto de solo lectura para datos de personas naturales.
 */
public interface PersonOutputPort {

    /**
     * Busca los datos de una persona natural (nombre y documento de
     * identidad) por su id.
     */
    Optional<DatosPersona> findById(Long ide);
}
