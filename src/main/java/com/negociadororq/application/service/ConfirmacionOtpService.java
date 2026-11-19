package com.negociadororq.application.service;

import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;

/**
 * Fase 28d (A1): confirmación del código OTP de una firma, validado por HTTP contra ms-security-util.
 */
public interface ConfirmacionOtpService {

    /**
     * Valida el código del pendiente del actor y, con la respuesta definitiva de security, sigue la firma (o deja el
     * pendiente como corresponda). Si security no responde, no cambia nada y lanza
     * {@link com.negociadororq.application.exception.ServicioDependienteNoDisponibleException} (503): el reintento del
     * actor usa la misma clave de idempotencia.
     *
     * @param request OTP tecleado por el actor y su identificación (negociación, empresa, operador, IP)
     */
    void confirmarOtp(ConfirmacionOtpRequestDto request);
}
