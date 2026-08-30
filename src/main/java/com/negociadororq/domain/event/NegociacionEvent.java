package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro de una acción de negocio ocurrida sobre una negociación (creación, aprobación,
 * rechazo, etc.), con el actor que la disparó y el estado resultante.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegociacionEvent {
        private String idNegociacion;
        private String numeroFactura;
        private String rutActor;
        private String rolActor;
        private String accion;
        private String estado;
}


