package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoResponseDto {
    private String idNegociacion;
    private String estadoEndoso;   // EJ: PENDIENTE, ENDOSADO, RECHAZADO
    private List<String> facturasEndosadas;
    private List<String> facturasRechazadas;

    private String plantillaHtml;

}
