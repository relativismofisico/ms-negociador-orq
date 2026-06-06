package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.PendingOtp;

import java.util.Optional;

public interface PendingOtpOutputPort {
    PendingOtp save(PendingOtp pendingOtp);
    Optional<PendingOtp> findByIdNegociacion(String idNegociacion);
    void deleteByIdNegociacion(String idNegociacion);
}
