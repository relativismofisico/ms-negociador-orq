package com.negociadororq.application.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Tipo y clase de operación solicitados al crear una negociación. */
@Data
public class OperacionDto {
    /** Ejemplo: FACTORING, CONFIRMING. */
    @NotNull(message = "El tipo de operación es obligatorio")
    private String tipo;

    /** Ejemplo: GARANTIA, ANTICIPO. */
    @NotNull(message = "La clase de operación es obligatoria")
    private String clase;
}

