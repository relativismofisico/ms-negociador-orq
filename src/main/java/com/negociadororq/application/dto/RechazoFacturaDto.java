package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fase 28d: una factura de la petición que no entró en la negociación, con su código y su motivo (el mismo texto que
 * {@code errores}), y los datos del cupo indirecto cuando el motivo es {@code SIN_CUPO_INDIRECTO}.
 */
@Schema(description = "Factura no incluida en la negociación")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RechazoFacturaDto {

    @Schema(description = "Número de la factura", example = "FE-1001")
    private String numeroFactura;

    @Schema(description = "Código del motivo", example = "SIN_CUPO_INDIRECTO")
    private String codigo;

    @Schema(description = "Motivo legible (igual al de errores)")
    private String mensaje;

    @Schema(description = "Solo con SIN_CUPO_INDIRECTO: disponible, requerido, faltante, suma de la petición y nombres")
    private DetalleCupoIndirectoDto cupoIndirecto;
}
