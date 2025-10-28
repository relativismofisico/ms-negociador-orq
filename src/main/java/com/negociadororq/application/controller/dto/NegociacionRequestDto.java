package com.negociadororq.application.controller.dto;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Operacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegociacionRequestDto {
    private String rutEmpresa;
    private String rolEmpresa;
    private Operacion tipoOperacion; // FACTORING o CONFIRMING
    private List<FacturaNegociada> facturas; // cada factura puede tener fondeador propio
    private List<FondeadorAsignacion> fondeadoresGlobales; // opcional: fondeadores aplicables a todas las facturas
    private String rutOperador;
}
