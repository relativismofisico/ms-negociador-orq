package com.negociadororq.domain.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Tipo y clase de operación (Factoring/Confirming) sobre la que se negocia.
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class Operacion {

    private String tipo;
    private String clase;

}

