package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Decisión de aprobación individual sobre una factura puntual dentro de una solicitud
 * de aprobación que cubre varias facturas a la vez.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionFacturaRequest {
    private String numeroFactura;
    private boolean aprobado;
    private String observacion;
}
