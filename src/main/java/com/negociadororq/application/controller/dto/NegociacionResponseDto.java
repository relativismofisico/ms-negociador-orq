package com.negociadororq.application.controller.dto;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Operacion;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Builder;
import java.util.List;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegociacionResponseDto {
    private String idNegociacion;
    private String rutEmpresa;
    private Operacion tipoOperacion;
    private List<FacturaNegociada> facturas;
    private List<FondeadorAsignacion> fondeadoresAsignados;
    private BigDecimal totalNegociado;
    private String estado; // ej: PENDIENTE, ACEPTADA, RECHAZADA
    private List<String> errores;
}
