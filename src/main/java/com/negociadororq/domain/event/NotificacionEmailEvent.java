package com.negociadororq.domain.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEmailEvent {

    private String idEvento;
    private String idNegociacion;

    // destinatarios
    private List<DestinatarioEmail> destinatarios;
    private String tipoNotificacion;
    private String asunto;
    private Map<String, Object> data;
    private LocalDateTime fecha;
}
