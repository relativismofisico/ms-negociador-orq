package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.PendingOtp;

/**
 * Puerto de persistencia de solicitudes OTP pendientes de aprobación.
 */
public interface PendingOtpOutputPort {

    /**
     * @param pendingOtp solicitud OTP a persistir
     * @return la solicitud persistida
     */
    PendingOtp save(PendingOtp pendingOtp);

    /**
     * @param idNegociacion id de la negociación
     * @return la solicitud OTP pendiente para esa negociación, si existe
     */
    Optional<PendingOtp> findByIdNegociacion(String idNegociacion);

    /**
     * @param idNegociacion id de la negociación cuya solicitud OTP se elimina
     */
    void deleteByIdNegociacion(String idNegociacion);
}
