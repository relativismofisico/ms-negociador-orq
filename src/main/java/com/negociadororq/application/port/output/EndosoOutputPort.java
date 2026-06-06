package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.Endoso;

import java.util.Optional;

public interface EndosoOutputPort {
    boolean existsByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);
    Optional<Endoso> findByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura);
    Endoso save(Endoso endoso);
}
