package com.negociadororq.domain.event;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado a Kafka cuando se solicita un código OTP para aprobar una
 * negociación, para que el servicio de notificaciones lo genere y lo envíe.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpRequestEvent {

    private String rutEmpresa;
    private String rutOperador;
    private String idNegociacion;
    private String rolActor;
    private LocalDateTime fechaSolicitud;

}

