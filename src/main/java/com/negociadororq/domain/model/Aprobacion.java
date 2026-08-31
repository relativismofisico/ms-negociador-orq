package com.negociadororq.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado puntual de una decisión de aprobación (aprobado o no, y cuándo se tomó).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Aprobacion {
    private boolean aprobado;
    private LocalDateTime fecha;
}

