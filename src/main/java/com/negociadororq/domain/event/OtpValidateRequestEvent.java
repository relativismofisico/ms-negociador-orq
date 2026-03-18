package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpValidateRequestEvent {

    private String idNegociacion;
    private String rutEmpresa;
    private String rutOperador;
    private String otp;
    private String ipAprobacion;
}
