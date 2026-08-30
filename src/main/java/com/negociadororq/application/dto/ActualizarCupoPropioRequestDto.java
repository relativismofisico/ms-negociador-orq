package com.negociadororq.application.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para definir el cupo propio de autofondeo de una empresa para Confirming.
 */
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

    @Schema(description = "Tasa que la empresa define para sus propias operaciones de Confirming autofondeadas "
        + "(se muestra como \"Tarifa Plena\" cuando tipoDescuento es FIJO, o como \"Tasa\" en los demás casos)",
        example = "2.5")
    @NotNull
    @PositiveOrZero
    private BigDecimal tasa;

    @Schema(description = "Tipo de descuento con el que se autofondea: FIJO (tarifa plena, sin tipo de tasa), "
        + "FINANCIERO o DINAMICO (ambos exigen tipoTasa)", example = "FIJO",
        allowableValues = {"FIJO", "FINANCIERO", "DINAMICO"})
    @NotNull
    private String tipoDescuento;

    @Schema(description = "Tipo de tasa: obligatorio salvo que tipoDescuento sea FIJO",
        example = "TASA_EFECTIVA_ANUAL",
        allowableValues = {"TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO"})
    private String tipoTasa;

    @Schema(description = "Tarifa plena: obligatoria solo cuando tipoDescuento es DINAMICO (ahí coexiste con "
        + "tasa, a diferencia de FIJO donde tasa se muestra como \"Tarifa Plena\" y esta no aplica)",
        example = "3.0")
    private BigDecimal tarifaPlena;

    @Schema(description = "Vigencia del descuento: durante todo el plazo de la factura, durante los primeros "
        + "diasVigencia días de la factura, o faltando diasVigencia días para su vencimiento", example = "TODO_EL_PLAZO",
        allowableValues = {"TODO_EL_PLAZO", "PRIMEROS_DIAS", "DIAS_ANTES_VENCIMIENTO"})
    @NotNull
    private String vigenciaDescuento;

    @Schema(description = "Días de vigencia: obligatorios (entero no negativo) salvo que vigenciaDescuento sea "
        + "TODO_EL_PLAZO", example = "5")
    @PositiveOrZero
    private Integer diasVigencia;

    @Schema(description = "Porcentaje que se retiene como reserva ante incertidumbre en el valor real a pagar "
        + "por la factura a su vencimiento, o cuando corresponde aplicar un descuento comercial sobre el valor "
        + "de la factura. Obligatorio, 0 por defecto", example = "0")
    @NotNull
    @PositiveOrZero
    private BigDecimal porcentajeReserva;
}
