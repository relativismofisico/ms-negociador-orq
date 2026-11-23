package com.negociadororq.application.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fase 28d (decisión de John): los datos de una factura que no entró por falta de cupo indirecto (FACTORING; el pagador
 * habilita a sus proveedores a través de una oferta del fondeador). Todos los montos son exactos ({@link BigDecimal}).
 */
@Schema(description = "Detalle del cupo indirecto insuficiente de una factura")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCupoIndirectoDto {

    @Schema(description = "NIT del fondeador de la oferta")
    private String rutFondeador;

    @Schema(description = "Nombre del fondeador de la oferta")
    private String fondeador;

    @Schema(description = "NIT del pagador")
    private String rutPagador;

    @Schema(description = "Nombre del pagador")
    private String pagador;

    @Schema(description = "Cupo disponible al llegar a esta factura (o en el momento del descuento)", example = "2000.00")
    private BigDecimal disponible;

    @Schema(description = "Valor de esta factura", example = "4000.00")
    private BigDecimal requerido;

    @Schema(description = "requerido - disponible", example = "2000.00")
    private BigDecimal faltante;

    @Schema(description = "Suma de las facturas de la petición con este mismo cupo (fondeador, pagador, proveedor)",
            example = "12000.00")
    private BigDecimal sumaPeticion;

    @Schema(description = "Cupo disponible de ese trío al empezar la petición", example = "10000.00")
    private BigDecimal disponibleInicial;
}
