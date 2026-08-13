package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Solicitud para activar/desactivar la rentabilidad mínima esperada del autofondeo, y su valor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentabilidadMinimaRequestDto {

    @Schema(description = "true para exigir una rentabilidad mínima, false para desactivarla", example = "true")
    @NotNull
    private Boolean activa;

    @Schema(description = "Rentabilidad mínima esperada, como tasa Efectivo Anual (EA). Obligatoria si activa=true.",
        example = "12.5")
    private BigDecimal valor;
}
