package com.negociadororq.domain.model;

import java.time.LocalDateTime;

import com.negociadororq.enums.RolActor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionActorDto {
    private String rutActor;
    private RolActor rolActor;
    private boolean aprobado;
    private String observacion;
    private LocalDateTime fecha;
}
