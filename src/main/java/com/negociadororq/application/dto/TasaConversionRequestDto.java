package com.negociadororq.application.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para convertir una tasa entre Tasa Efectiva Anual, Tasa Mes Vencido y.
 */
@Schema(description = "Solicitud para convertir una tasa entre Tasa Efectiva Anual, Tasa Mes Vencido y "
    + "Tasa Mes Anticipado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TasaConversionRequestDto {

    @Schema(description = "Valor de la tasa a convertir, expresado como porcentaje", example = "2.1")
    private BigDecimal valor;

    @Schema(description = "Tipo de tasa en la que está expresado el valor", example = "TASA_MES_VENCIDO",
        allowableValues = {"TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO"})
    private String tipoTasa;
}
