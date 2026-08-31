package com.negociadororq.application.port.output;

import com.negociadororq.domain.enums.EstadoNegociacion;

/**
 * Actualizaciones parciales (patch) sobre una negociación ya persistida, sin
 * necesidad de reescribir el agregado completo.
 */
public interface NegociacionPatchOutputPort {

    /**
     * @param negociacionId id de la negociación
     * @param estado        nuevo estado global de la negociación
     */
    void actualizarEstadoPorId(String negociacionId, EstadoNegociacion estado);

    /**
     * @param negociacionId  id de la negociación
     * @param numeroFactura  número de la factura cuyo endoso cambió
     * @param creacionEndoso si ya se creó el endoso para esa factura
     */
    void actualizarEstadoEndosoPorId(String negociacionId, String numeroFactura, Boolean creacionEndoso);
}
