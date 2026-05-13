package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidacionFacturaResponseDto {
    private boolean valida;
    private boolean vencida;
    private long diasHastaVencimiento;
    private String codigo;
    private String mensaje;
}
