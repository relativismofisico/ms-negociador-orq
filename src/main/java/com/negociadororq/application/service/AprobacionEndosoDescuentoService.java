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

    /**
     * Fase 28d (P-09, prompt 2.5): para el aviso del front, cuántas negociaciones esperan la firma de la empresa porque
     * quedaron pedidas por un operador hoy desactivado (cualquier operador activo de la empresa puede firmarlas).
     *
     * @param rutEmpresa NIT de la empresa
     * @return cuántas
     */
    default long contarEsperandoFirma(String rutEmpresa) {
        return 0L;
    }
}

