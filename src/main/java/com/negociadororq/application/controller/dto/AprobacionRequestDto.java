package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionRequestDto {
    private String idNegociacion; // id de la negociación en mongo
    private String rutOperador;
    private String rutEmpresa;      // quien aprueba (pagador, fondeador, proveedor)
    private String rolActor;
    private List<AprobacionFacturaRequestDto> facturas; // facturas con su decisión// true = aprueba, false = rechaza
    private String observacion;     // opcional
}
