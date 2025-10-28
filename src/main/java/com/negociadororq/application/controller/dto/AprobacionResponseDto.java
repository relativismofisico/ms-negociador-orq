package com.negociadororq.application.controller.dto;

import com.negociadororq.domain.model.FacturaNegociada;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionResponseDto {
    private String idNegociacion;
    private String estado;
    private List<FacturaNegociada> facturas; // varias facturas dentro de la negociación// EJ: EN_APROBACION, RECHAZADA, APROBADA
    private List<String> errores;        // errores si aplica
}
