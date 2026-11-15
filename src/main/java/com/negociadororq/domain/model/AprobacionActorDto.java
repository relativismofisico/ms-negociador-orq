package com.negociadororq.domain.model;

import java.time.LocalDateTime;

import com.negociadororq.domain.enums.RolActor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Decisión de aprobación de un actor puntual (identificado por RUT y rol) sobre una
 * negociación, con la observación y fecha en que la tomó.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionActorDto {
    private String rutActor;
    private RolActor rolActor;
    private boolean aprobado;
    private String observacion;
    private LocalDateTime fecha;
    /** Fase 28c: OTP con el que se firmó (idempotencia: la misma validación no se aplica dos veces). */
    private String otpId;

    /**
     * @param rutActor    RUT del actor
     * @param rolActor    rol
     * @param aprobado    decisión
     * @param observacion observación
     * @param fecha       instante de la decisión
     */
    public AprobacionActorDto(String rutActor, RolActor rolActor, boolean aprobado, String observacion,
            LocalDateTime fecha) {
        this(rutActor, rolActor, aprobado, observacion, fecha, null);
    }
}

