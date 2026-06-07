package com.negociadororq.application.exception.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Estructura estándar de respuesta de error para todos los endpoints")
@Getter
@Builder
public class ErrorResponse {

    @Schema(description = "Fecha y hora del error en el servidor.", example = "2026-06-07T12:00:00")
    private final LocalDateTime timestamp;

    @Schema(description = "Código numérico HTTP.", example = "404")
    private final int status;

    @Schema(description = "Descripción estándar HTTP.", example = "Not Found")
    private final String error;

    @Schema(description = "Código de error de dominio en SNAKE_UPPER_CASE.", example = "FACTURA_NO_ENCONTRADA")
    private final String codigo;

    @Schema(description = "Mensaje legible por humanos.", example = "Factura no encontrada: FECI3566")
    private final String mensaje;

    @Schema(description = "URI del endpoint que generó el error.", example = "/negociar/factura")
    private final String path;

    @Schema(description = "Errores detallados de validación de campos. Presente solo en errores 400.")
    private final List<String> detalles;
}
