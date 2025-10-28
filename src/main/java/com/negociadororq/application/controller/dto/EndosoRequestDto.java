package com.negociadororq.application.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoRequestDto {
    private String idNegociacion;
    private String rutProveedor;    // quien endosa
    private boolean endosado;       // true = realizó endoso
}
