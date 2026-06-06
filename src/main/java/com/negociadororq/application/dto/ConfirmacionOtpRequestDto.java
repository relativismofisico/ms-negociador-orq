package com.negociadororq.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacionOtpRequestDto {

    private String idNegociacion;
    private String rutEmpresa;
    private String rutOperador;
    private String otp;
    private String ipAprobacion;
    private List<AprobacionFacturaRequestDto> facturas;
}


