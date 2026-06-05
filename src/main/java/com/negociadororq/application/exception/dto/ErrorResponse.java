package com.negociadororq.application.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estructura estándar corporativa de respuesta de error.
 * Todos los microservicios de la plataforma deben usar esta estructura.
 *
 * Ejemplo de respuesta:
 * {
 *   "timestamp": "2026-06-05T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "codigo": "FACTURA_NO_ENCONTRADA",
 *   "mensaje": "Factura no encontrada: FAC-001",
 *   "path": "/validaciones/facturas/vencimiento",
 *   "detalles": ["campo: mensaje de error"]  // solo en errores de validación
 * }
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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