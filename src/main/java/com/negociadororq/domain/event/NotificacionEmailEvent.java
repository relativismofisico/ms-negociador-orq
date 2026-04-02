package com.negociadororq.domain.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEmailEvent {

    private String idNegociacion;

    // destinatarios
    private List<DestinatarioEmail> destinatarios;
    private String tipoNotificacion;
    private String asunto;
    private String cuerpo;
    private LocalDateTime fecha;
}
