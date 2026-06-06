package com.negociadororq.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
