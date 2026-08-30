package com.negociadororq.application.port.output;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.model.AprobacionActorDto;

/**
 * Actualizaciones parciales (patch) sobre un endoso ya persistido.
 */
public interface EndosoPatchOutputPort {

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param estado        nuevo estado del endoso
     */
    void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estado);

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param aprobacion    aprobación del actor a registrar
     * @param estado        nuevo estado del endoso tras registrar la aprobación
     */
    void registrarAprobacionActor(String idNegociacion, String numeroFactura, AprobacionActorDto aprobacion, EstadoEndoso estado);
}
