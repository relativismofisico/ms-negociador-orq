package com.negociadororq.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Fase 28d (H): validación de un OTP por HTTP contra ms-security-util.
 */
public final class ValidacionOtp {

    private ValidacionOtp() {
    }

    /**
     * Solicitud. El código nunca sale en {@code toString}.
     *
     * @param idNegociacion     negociación
     * @param rutEmpresa        empresa del actor
     * @param rutOperador       operador
     * @param otp               código tecleado
     * @param ipAprobacion      IP del actor
     * @param claveIdempotencia clave estable de esta validación (la misma al reintentar)
     */
    public record Solicitud(String idNegociacion, String rutEmpresa, String rutOperador, String otp,
            String ipAprobacion, String claveIdempotencia) {

        @Override
        public String toString() {
            return "ValidacionOtp.Solicitud[idNegociacion=" + idNegociacion + ", rutEmpresa=" + rutEmpresa
                    + ", rutOperador=" + rutOperador + ", otp=***, claveIdempotencia=" + claveIdempotencia + "]";
        }
    }

    /**
     * Resultado, sin el código.
     *
     * @param valido             si el código era correcto y se canjeó
     * @param motivo             con {@code valido=false}: INCORRECTO, BLOQUEADO, EXPIRADO o USADO
     * @param intentosRestantes  intentos antes del bloqueo
     * @param otpId              id del OTP en security
     * @param fechaValidacion    con {@code valido=true}: fecha de la validación
     * @param instanteValidacion con {@code valido=true}: el mismo instante, con zona
     */
    public record Resultado(boolean valido, String motivo, int intentosRestantes, String otpId,
            LocalDateTime fechaValidacion, Instant instanteValidacion) {
    }
}
