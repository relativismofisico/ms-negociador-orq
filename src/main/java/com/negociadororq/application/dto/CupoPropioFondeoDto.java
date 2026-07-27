package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Cupo propio de autofondeo de una empresa para Confirming, y el tope asignado por el administrador")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CupoPropioFondeoDto {

    @Schema(description = "Identificador de la empresa", example = "1")
    private Long companyId;

    @Schema(description = "Cupo de autofondeo definido por la propia empresa", example = "50000000")
    private BigDecimal cupoPropio;

    @Schema(description = "Cupo de autofondeo disponible (no consumido)", example = "50000000")
    private BigDecimal cupoDisponible;

    @Schema(description = "Cupo máximo permitido, asignado por el administrador de la plataforma", example = "100000000")
    private BigDecimal cupoMaximoAdministrador;

    @Schema(description = "Tasa que la empresa define para sus propias operaciones de Confirming autofondeadas",
        example = "2.5")
    private BigDecimal tasa;

    @Schema(description = "Si la empresa permite agrupar varias facturas autofondeadas en una sola solicitud "
        + "de descuento (true), o exige una solicitud por factura (false, por defecto)", example = "false")
    private boolean endosoGrupal;
}
