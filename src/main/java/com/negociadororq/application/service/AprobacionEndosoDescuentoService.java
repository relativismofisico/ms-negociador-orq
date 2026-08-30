package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;

/**
 * Orquesta la aprobación de un endoso de descuento vía OTP: primero se
 * solicita el código, luego se confirma.
 */
public interface AprobacionEndosoDescuentoService {

    /**
     * @param request datos del actor y endoso para el cual se solicita el OTP
     */
    void solicitarOtp(AprobacionRequestDto request);

    /**
     * @param request OTP recibido del actor y datos de la aprobación a confirmar
     */
    void confirmarOtp(ConfirmacionOtpRequestDto request);
}

