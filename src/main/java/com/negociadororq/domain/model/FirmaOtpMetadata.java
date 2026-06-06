package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

