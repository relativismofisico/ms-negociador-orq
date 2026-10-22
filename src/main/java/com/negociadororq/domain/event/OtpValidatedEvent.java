package com.negociadororq.domain.event;

import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Evento publicado a Kafka con el resultado final de la validación del OTP, ya listo
 * para que el listener correspondiente registre la aprobación u obligue a reintentar.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpValidatedEvent {

    private String idNegociacion;
    private String rutEmpresa;
    private boolean valido;
    private String rutOperador;
    @ToString.Exclude
    private String otp;
    private String ip;
    private String ipAprobacion;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}

