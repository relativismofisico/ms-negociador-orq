package com.negociadororq.application.port.output;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * @param id id de la solicitud a descartar
     */
    void deleteById(String id);

    /**
     * @param estado estado de la firma (Fase 28c)
     * @param limite última transición anterior a este instante
     * @return las solicitudes en ese estado sin cambios desde el límite
     */
    List<PendingOtp> findByEstadoAndFechaActualizacionBefore(String estado, LocalDateTime limite);
}
