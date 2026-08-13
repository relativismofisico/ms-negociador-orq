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

    @Schema(description = "Si la empresa exige una rentabilidad mínima esperada para autofondear (false por defecto)",
        example = "false")
    private boolean rentabilidadMinimaActiva;

    @Schema(description = "Rentabilidad mínima esperada por la empresa pagadora-fondeadora, expresada como "
        + "tasa Efectivo Anual (EA). Solo aplica si rentabilidadMinimaActiva es true.", example = "12.5")
    private BigDecimal rentabilidadMinima;

    @Schema(description = "Tipo de descuento con el que se autofondea", example = "FIJO",
        allowableValues = {"FIJO", "FINANCIERO", "DINAMICO"})
    private String tipoDescuento;

    @Schema(description = "Tipo de tasa asociado a tasa. Null cuando tipoDescuento es FIJO.",
        example = "TASA_EFECTIVA_ANUAL",
        allowableValues = {"TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO"})
    private String tipoTasa;

    @Schema(description = "Tarifa plena. Solo se completa cuando tipoDescuento es DINAMICO.", example = "3.0")
    private BigDecimal tarifaPlena;

    @Schema(description = "Vigencia del descuento", example = "TODO_EL_PLAZO",
        allowableValues = {"TODO_EL_PLAZO", "PRIMEROS_DIAS", "DIAS_ANTES_VENCIMIENTO"})
    private String vigenciaDescuento;

    @Schema(description = "Días de vigencia. Null cuando vigenciaDescuento es TODO_EL_PLAZO.", example = "5")
    private Integer diasVigencia;

    @Schema(description = "Porcentaje que se retiene como reserva ante incertidumbre en el valor real a pagar "
        + "por la factura a su vencimiento, o cuando corresponde aplicar un descuento comercial sobre el valor "
        + "de la factura", example = "0")
    private BigDecimal porcentajeReserva;
}
