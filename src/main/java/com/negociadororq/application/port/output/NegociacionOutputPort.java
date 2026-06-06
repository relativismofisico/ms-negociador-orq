package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.Negociacion;

import java.util.Optional;

public interface NegociacionOutputPort {
    Optional<Negociacion> findById(String id);
    Negociacion save(Negociacion negociacion);
    Optional<Negociacion> findByNumeroFactura(String numeroFactura);
}
