package com.negociadororq.application.exception.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp;

    /** Código numérico HTTP (ej: 404, 422) */
    private final int status;

    /** Descripción estándar HTTP (ej: "Not Found", "Unprocessable Entity") */
    private final String error;

    /** Código de error de dominio en SNAKE_UPPER_CASE (ej: FACTURA_NO_ENCONTRADA) */
    private final String codigo;

    /** Mensaje legible por humanos */
    private final String mensaje;

    /** URI del endpoint que generó el error */
    private final String path;

    /** Lista de errores detallados (usado en validaciones de campos) */
    private final List<String> detalles;
}

