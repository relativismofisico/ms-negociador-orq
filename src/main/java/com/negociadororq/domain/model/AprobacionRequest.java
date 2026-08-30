package com.negociadororq.domain.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de un operador para aprobar o rechazar, factura por factura, las facturas
 * de una negociación.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionRequest {
    private String idNegociacion;
    private String rutOperador;
    private String rutEmpresa;
    private String rolActor;
    private List<AprobacionFacturaRequest> facturas;
    private String observacion;
}
