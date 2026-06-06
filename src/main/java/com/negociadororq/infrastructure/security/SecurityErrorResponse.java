package com.negociadororq.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta estándar de error para las capas de seguridad (401 / 403).
 * Autocontenida en el paquete de seguridad para evitar dependencias circulares
 * con la capa de aplicación.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityErrorResponse {
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String codigo;
    private final String mensaje;
    private final String path;
    private final List<String> detalles;
}

