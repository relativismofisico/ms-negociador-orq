package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.DatosPersona;
import java.util.Optional;

public interface PersonOutputPort {

    /**
     * Busca los datos de una persona natural (nombre y documento de
     * identidad) por su id.
     */
    Optional<DatosPersona> findById(Long ide);
}
