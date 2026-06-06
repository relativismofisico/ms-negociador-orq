package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpRequestEvent {

    private String rutEmpresa;
    private String rutOperador;
    private String idNegociacion;
    private String rolActor;
    private LocalDateTime fechaSolicitud;

}

