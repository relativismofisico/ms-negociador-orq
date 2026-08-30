package com.negociadororq.domain.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento publicado a Kafka para disparar el envío de una notificación por email a uno
 * o varios destinatarios, con la plantilla (`tipoNotificacion`) y los datos a rellenar.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEmailEvent {

    private String idEvento;
    private String idNegociacion;
    private List<DestinatarioEmail> destinatarios;
    private String tipoNotificacion;
    private String asunto;
    private Map<String, Object> data;
    private LocalDateTime fecha;
}

