package com.negociadororq.domain.model;

import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
    @ToString.Exclude
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

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}
