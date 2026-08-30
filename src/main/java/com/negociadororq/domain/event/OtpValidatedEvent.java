package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado a Kafka con el resultado final de la validación del OTP, ya listo
 * para que el listener correspondiente registre la aprobación u obligue a reintentar.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpValidatedEvent {

    private String idNegociacion;
    private String rutEmpresa;
    private boolean valido;
    private String rutOperador;
    private String otp;
    private String ip;
    private String ipAprobacion;
}

