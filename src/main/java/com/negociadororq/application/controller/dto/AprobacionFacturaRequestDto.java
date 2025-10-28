package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionFacturaRequestDto {
    private String numeroFactura;
    private boolean aprobado;
    private String observacion;
}
