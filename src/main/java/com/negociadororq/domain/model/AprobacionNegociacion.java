package com.negociadororq.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Decisión de aprobación de una empresa (identificada por RUT y rol) sobre una
 * negociación, persistida para llevar el historial de quién aprobó/rechazó y cuándo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionNegociacion {

    private String id;
    private String negociacionId;
    private String rutEmpresa;
    private String rol;
    private boolean aprobado;
    private String observacion;
    private LocalDateTime fecha;
}
