package com.negociadororq.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Metadatos de auditoría de una firma vía OTP: quién la hizo, desde qué IP, si fue
 * aprobada y con qué observación.
 */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FirmaOtpMetadata {

    private String rutEmpresa;
    private String rutOperador;
    private String rolActor;

    private String otp;
    private String ip;

    private boolean aprobado;

    private LocalDateTime fecha;

    private String observacion;
}

