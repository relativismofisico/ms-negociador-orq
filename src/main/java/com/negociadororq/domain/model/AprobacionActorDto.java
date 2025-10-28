package com.negociadororq.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionActorDto {
    private String rutActor;         // proveedor, pagador, fondeador
    private boolean aprobado;   // true = aprueba, false = rechaza
    private String observacion; // opcional
    private LocalDateTime fecha;
}