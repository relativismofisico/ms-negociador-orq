package com.negociadororq.domain.model;

import lombok.Data;

/**
 * Plantilla de mensaje reutilizable (ej. para notificaciones), con su contenido y si
 * está activa para usarse.
 */
@Data
public class MessageTemplate {

    private Long id;
    private String templateName;
    private String content;
    private boolean active;
}
