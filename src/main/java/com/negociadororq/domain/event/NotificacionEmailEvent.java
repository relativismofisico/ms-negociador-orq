package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private List<DestinatarioEmail> destinatarios;
    private String tipoNotificacion;
    private String asunto;
    private Map<String, Object> data;
    private LocalDateTime fecha;
}
