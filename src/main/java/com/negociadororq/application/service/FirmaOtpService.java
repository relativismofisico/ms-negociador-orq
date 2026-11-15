package com.negociadororq.application.service;

import com.negociadororq.domain.event.OtpValidatedEvent;

/**
 * Resultado de la validación del OTP en ms-security-util y firma del endoso (Fase 28c): máquina de estados
 * {@code PENDIENTE_OTP -> OTP_VALIDADO -> FIRMADO / FALLO_FIRMA}.
 */
public interface FirmaOtpService {

    /**
     * @param event {@code otp.validate.response}
     */
    void procesarValidacion(OtpValidatedEvent event);

    /**
     * Reintenta las firmas que quedaron en {@code OTP_VALIDADO} (caída a mitad de la firma o fallo transitorio).
     *
     * @return cuántas se reintentaron
     */
    int reintentarPendientes();
}
