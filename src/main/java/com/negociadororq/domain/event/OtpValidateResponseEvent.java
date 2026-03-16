package com.negociadororq.domain.event;

import lombok.Data;

@Data
public class OtpValidateResponseEvent {

    private String idNegociacion;
    private String rutOperador;
    private boolean valido;
    private String mensaje;

}
