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

    @Schema(description = "Fase 28d: CUFE de la factura (opcional). Si no viene, la factura se identifica por número y, "
        + "si vienen, los RUT de proveedor y pagador; con más de una coincidencia responde 409 FACTURA_AMBIGUA.")
    private String cufe;

    @Schema(description = "RUT del proveedor de la factura (opcional).")
    private String rutProveedor;

    @Schema(description = "RUT del pagador de la factura (opcional).")
    private String rutPagador;

    @Schema(description = "Fecha de proceso contra la cual se evalúa el vencimiento (formato ISO 8601).",
        example = "2026-06-07", type = "string", format = "date")
    private LocalDate fechaProceso;
}
