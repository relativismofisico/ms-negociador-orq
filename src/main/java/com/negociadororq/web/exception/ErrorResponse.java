package com.negociadororq.web.exception;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Estructura estándar de respuesta de error para todos los endpoints.
 */
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

    @Schema(description = "Igual que 'detalles' pero estructurado por campo en vez de un string concatenado "
            + "('campo: mensaje'). Presente solo en errores 400 de validación; el frontend todavía usa "
            + "'mensaje'/'detalles' para el toast genérico, este campo queda disponible para cuando se "
            + "implemente resaltado por campo.")
    private final List<FieldErrorDetail> fieldErrors;

    /**
     * Un error de validación puntual: qué campo falló, qué valor se rechazó y por qué. Mismo contrato
     * {@code field}/{@code rejectedValue}/{@code message} que ms-notificaciones-util, tomado como el
     * estándar único de la plataforma para este detalle estructurado.
     *
     * @param field         nombre del campo del body que falló la validación
     * @param rejectedValue valor recibido que fue rechazado (como texto; {@code null} si no aplica)
     * @param message       mensaje de validación de ese campo
     */
    public record FieldErrorDetail(String field, String rejectedValue, String message) {
    }
}
