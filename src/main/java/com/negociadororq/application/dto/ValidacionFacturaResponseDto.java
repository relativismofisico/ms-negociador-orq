package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de la validación de vencimiento de una factura.
 */
@Schema(description = "Resultado de la validación de vencimiento de una factura")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidacionFacturaResponseDto {

    @Schema(description = "Indica si la factura es válida para negociación (no vencida y en estado correcto).",
        example = "true")
    private boolean valida;

    @Schema(description = "Indica si la factura está vencida a la fecha de proceso indicada.", example = "false")
    private boolean vencida;

    @Schema(description = "Días restantes hasta el vencimiento. Negativo si ya venció.", example = "15")
    private long diasHastaVencimiento;

    @Schema(description = "Código de resultado de la validación.", example = "FACTURA_VIGENTE",
        allowableValues = {"FACTURA_VIGENTE", "FACTURA_VENCIDA", "FACTURA_NO_ENCONTRADA"})
    private String codigo;

    @Schema(description = "Mensaje descriptivo del resultado.", example = "La factura tiene 15 días hasta su vencimiento.")
    private String mensaje;
}
