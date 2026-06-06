package com.negociadororq.application.port.output;

import com.negociadororq.domain.enums.EstadoNegociacion;

public interface NegociacionPatchOutputPort {
    void actualizarEstadoPorId(String negociacionId, EstadoNegociacion estado);
    void actualizarEstadoEndosoPorId(String negociacionId, String numeroFactura, Boolean creacionEndoso);
}
