package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.enums.EstadoEndoso;

public interface EndosoPatchOutputPort {
    void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estado);
    void registrarAprobacionActor(String idNegociacion, String numeroFactura, AprobacionActorDto aprobacion, EstadoEndoso estado);
}
