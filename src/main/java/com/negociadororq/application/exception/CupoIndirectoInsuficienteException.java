package com.negociadororq.application.exception;

import com.negociadororq.application.dto.DetalleCupoIndirectoDto;

/**
 * Fase 28d (decisión de John): la factura no cabe en el cupo indirecto del proveedor. Es un rechazo de negocio de esa
 * factura (las demás siguen); lleva los montos exactos para la respuesta.
 */
public class CupoIndirectoInsuficienteException extends ReglaDeNegocioException {

    /** Código del rechazo. */
    public static final String CODIGO = "SIN_CUPO_INDIRECTO";

    private final transient DetalleCupoIndirectoDto detalle;

    /**
     * @param mensaje texto para el cliente
     * @param detalle montos y nombres
     */
    public CupoIndirectoInsuficienteException(String mensaje, DetalleCupoIndirectoDto detalle) {
        super(mensaje, CODIGO);
        this.detalle = detalle;
    }

    /** @return montos y nombres del rechazo */
    public DetalleCupoIndirectoDto getDetalle() {
        return detalle;
    }
}
