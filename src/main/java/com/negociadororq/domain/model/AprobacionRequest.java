package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
