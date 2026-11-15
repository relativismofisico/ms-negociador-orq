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
    /** Fase 28c: referencia del OTP en security. */
    private String otpId;
    /** Fase 28c, con valido=false: INCORRECTO, EXPIRADO, BLOQUEADO o USADO. */
    private String motivo;
    /** Fase 28c, con valido=false: intentos que quedan antes del bloqueo. */
    private Integer intentosRestantes;
    /**
     * Fase 28c, con valido=true: fecha de la validación en security, en su hora local sin zona (formato anterior; se
     * conserva por compatibilidad). Usar {@link #instanteValidacion} cuando venga.
     */
    private java.time.LocalDateTime fechaValidacion;
    /** Fase 28c, con valido=true: instante (con zona, ISO-8601 en UTC) de la validación en security. */
    private java.time.Instant instanteValidacion;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}

