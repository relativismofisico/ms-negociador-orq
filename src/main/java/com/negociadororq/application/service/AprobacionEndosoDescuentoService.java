package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionRequestDto;

/**
 * Orquesta la aprobación de un endoso de descuento vía OTP: aquí se solicita el código; la confirmación va por
 * {@link ConfirmacionOtpService} (Fase 28d, A1: validación por HTTP).
 */
public interface AprobacionEndosoDescuentoService {

    /**
     * @param request datos del actor y endoso para el cual se solicita el OTP
     */
    void solicitarOtp(AprobacionRequestDto request);
}

