package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionResponseDto;
import com.negociadororq.domain.model.PendingOtp;

/**
 * Aprueba internamente (sin pasar por el flujo de OTP de un actor externo)
 * una solicitud pendiente, para casos de aprobación automática/interna.
 */
public interface AprobacionInternaService {

    /**
     * @param pending solicitud OTP pendiente a aprobar internamente
     * @return el resultado de la aprobación
     */
    AprobacionResponseDto aprobarInterno(PendingOtp pending);
}

