package com.negociadororq.application.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OperacionDto {
    @NotNull(message = "El tipo de operación es obligatorio")
    private String tipo; // Ejemplo: FACTORING, CONFIRMING

    @NotNull(message = "La clase de operación es obligatoria")
    private String clase; // Ejemplo: GARANTIA, ANTICIPO

}

