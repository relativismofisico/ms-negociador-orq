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
public class EndosoRequestDto {
    private String idNegociacion;
    private String rutActor;    // quien endosa

    /**
     * Lista de facturas a endosar.
     * Si viene vacía o null → error
     */
    private List<String> numerosFactura;
}
