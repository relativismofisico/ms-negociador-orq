package com.negociadororq.domain.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class Operacion {

    private String tipo;
    private String clase;

}
