package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.ValidacionOtp;

/**
 * Fase 28d (H): valida un OTP de forma síncrona en ms-security-util ({@code POST /auth/internal/otp/validacion}).
 */
public interface ValidacionOtpOutputPort {

    /**
     * @param solicitud negociación, empresa, operador, código, IP del actor y clave de idempotencia
     * @return el resultado de security (sin el código)
     * @throws com.negociadororq.application.exception.ServicioDependienteNoDisponibleException si security no responde,
     *         tarda más que el timeout o responde 5xx: nada cambió y se puede reintentar con la misma clave
     */
    ValidacionOtp.Resultado validar(ValidacionOtp.Solicitud solicitud);
}
