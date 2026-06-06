package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOtp {

    private String id;
    private String idNegociacion;
    private String rutEmpresa;
    private String rolActor;
    private String ip;
    private String otp;
    private Boolean aprobado;
    private AprobacionRequest request;
}
