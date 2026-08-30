package com.negociadororq.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Clase Dto de la liquidacion de la factura.
 *
 * @author John Redondo
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OperacionRequest {

    @NotEmpty(message = "El numero de factura es obligatorio")
    private String numeroFactura;

    @NotNull(message = "El tipo de operacion es obligatorio")
    private OperacionDto operacion;

}

