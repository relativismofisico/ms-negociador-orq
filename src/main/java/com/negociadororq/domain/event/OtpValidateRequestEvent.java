package com.negociadororq.domain.event;

import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Evento publicado a Kafka para pedirle al servicio de OTP que valide el código
 * ingresado por el operador al aprobar una negociación.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpValidateRequestEvent {

    private String idNegociacion;
    private String rutEmpresa;
    private String rutOperador;
    @ToString.Exclude
    private String otp;
    private String ipAprobacion;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}

