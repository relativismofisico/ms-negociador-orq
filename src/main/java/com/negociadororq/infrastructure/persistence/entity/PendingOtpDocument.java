package com.negociadororq.infrastructure.persistence.entity;

import com.negociadororq.domain.model.AprobacionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistencia MongoDB de un {@link com.negociadororq.domain.model.PendingOtp}: la solicitud
 * de aprobación de un actor a la espera de que confirme con el OTP enviado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pending_otp")
public class PendingOtpDocument {

    @Id
    private String id;
    private String idNegociacion;
    private String rutEmpresa;
    private String rolActor;
    private String ip;
    private String otp;
    private Boolean aprobado;
    private AprobacionRequest request;
    private Boolean validadoEnOrigen;
}
