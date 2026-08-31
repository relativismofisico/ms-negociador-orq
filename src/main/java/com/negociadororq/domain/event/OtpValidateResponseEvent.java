package com.negociadororq.domain.event;

import lombok.Data;

/**
 * Respuesta a un {@link OtpValidateRequestEvent}: si el código OTP ingresado fue
 * válido para la negociación, o el motivo del rechazo si no lo fue.
 */
@Data
public class OtpValidateResponseEvent {

    private String idNegociacion;
    private String rutOperador;
    private boolean valido;
    private String mensaje;

}

