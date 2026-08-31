package com.negociadororq.application.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para validar si una factura está vencida a una fecha determinada.
 */
@Schema(description = "Solicitud para validar si una factura está vencida a una fecha determinada")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidacionFacturaRequestDto {

    @Schema(description = "Número identificador de la factura a validar.", example = "FECI3566")
    private String numeroFactura;

    @Schema(description = "Fecha de proceso contra la cual se evalúa el vencimiento (formato ISO 8601).",
        example = "2026-06-07", type = "string", format = "date")
    private LocalDate fechaProceso;
}
