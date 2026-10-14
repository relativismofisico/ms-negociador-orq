package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de aprobación de negociación que quedó pendiente de que su OTP sea
 * validado, con el request original guardado para aplicarlo una vez confirmado.
 */
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

    /**
     * La solicitud de OTP que creó este pendiente pasó el guard de ownership
     * (CompanyOwnershipGuard) en el controller. Se propaga al evento de
     * liquidación como marca de trazabilidad.
     */
    private Boolean validadoEnOrigen;

    /** Actor (autenticado) y requestId de la solicitud del OTP: la aprobación se audita con ellos, en otro hilo. */
    private String actorTipo;
    private String actorId;
    private String actorRol;
    private Long actorEmpresaId;
    private String requestId;
}
