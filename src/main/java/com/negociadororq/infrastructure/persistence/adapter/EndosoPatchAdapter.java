package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link EndosoPatchOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class EndosoPatchAdapter implements EndosoPatchOutputPort {

    private final EndosoPatchRepository repository;

    @Override
    public void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estado) {
        repository.actualizarEstadoEndosoPorNoFactura(idNegociacion, numeroFactura, estado);
    }

    @Override
    public void registrarAprobacionActor(String idNegociacion, String numeroFactura, AprobacionActorDto aprobacion, EstadoEndoso estado) {
        repository.registrarAprobacionActor(idNegociacion, numeroFactura, aprobacion, estado);
    }
}
