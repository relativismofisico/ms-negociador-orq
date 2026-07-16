package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Solicitud para definir el cupo propio de autofondeo de una empresa para Confirming")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarCupoPropioRequestDto {

    @Schema(description = "Nuevo cupo de autofondeo. No puede exceder el cupo asignado por el administrador.",
        example = "50000000")
    @NotNull
    @PositiveOrZero
    private BigDecimal cupoPropio;
}
