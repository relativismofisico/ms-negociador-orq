package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.persistence.repository.NegociacionPatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link NegociacionPatchOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class NegociacionPatchAdapter implements NegociacionPatchOutputPort {

    private final NegociacionPatchRepository repository;

    @Override
    public void actualizarEstadoPorId(String negociacionId, EstadoNegociacion estado) {
        repository.actualizarEstadoPorId(negociacionId, estado);
    }

    @Override
    public void actualizarEstadoEndosoPorId(String negociacionId, String numeroFactura, Boolean creacionEndoso) {
        repository.actualizarEstadoEndosoPorId(negociacionId, numeroFactura, creacionEndoso);
    }
}
