package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoResponseDto {
    private String idNegociacion;
    private String estadoEndoso;   // EJ: PENDIENTE, ENDOSADO, RECHAZADO
}
