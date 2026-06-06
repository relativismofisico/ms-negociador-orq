package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.ConfirmacionOtpRequestDto;

public interface AprobacionEndosoDescuentoService {
    void solicitarOtp(AprobacionRequestDto request);
    void confirmarOtp(ConfirmacionOtpRequestDto request);
}
