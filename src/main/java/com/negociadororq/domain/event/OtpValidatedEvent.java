package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpValidatedEvent {

    private String idNegociacion;
    private String rutEmpresa;
    private boolean valido;
    private String rutOperador;
    private String otp;
    private String ip;
    private String ipAprobacion;
}

