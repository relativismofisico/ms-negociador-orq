package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Equivalencias de una tasa entre Tasa Efectiva Anual, Tasa Mes Vencido y "
    + "Tasa Mes Anticipado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TasaConversionResponseDto {

    @Schema(description = "Valor original informado, expresado como porcentaje", example = "2.1")
    private BigDecimal valorOriginal;

    @Schema(description = "Tipo de tasa en el que estaba expresado el valor original", example = "TASA_MES_VENCIDO",
        allowableValues = {"TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO"})
    private String tipoTasaOriginal;

    @Schema(description = "Equivalente en Tasa Efectiva Anual (EA), expresado como porcentaje", example = "28.3244")
    private BigDecimal tasaEfectivaAnual;

    @Schema(description = "Equivalente en Tasa Mes Vencido (interés compuesto pagado al final del período), "
        + "expresado como porcentaje", example = "2.1")
    private BigDecimal tasaMesVencido;

    @Schema(description = "Equivalente en Tasa Mes Anticipado (tasa de descuento pagada al inicio del período), "
        + "expresado como porcentaje", example = "2.0568")
    private BigDecimal tasaMesAnticipado;
}
