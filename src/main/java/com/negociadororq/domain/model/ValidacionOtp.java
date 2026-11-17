package com.negociadororq.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Fase 28d (H): validación de un OTP por HTTP contra ms-security-util.
 */
public final class ValidacionOtp {

    /** Largo máximo de la clave en security ({@code ValidacionOtpRequestDTO.LARGO_MAXIMO_CLAVE}). */
    public static final int LARGO_MAXIMO_CLAVE = 150;

    /** Caracteres que security acepta en la clave, sin el separador (lo agrega {@link #clave}). */
    private static final Pattern PARTE_SEGURA = Pattern.compile("^[A-Za-z0-9._:@+-]+$");

    private ValidacionOtp() {
    }

    /**
     * Clave de idempotencia de una validación: {@code idNegociacion|rutEmpresa|rutOperador|n}, con {@code n} = respuestas
     * definitivas de security ya recibidas para ese pendiente ({@code PendingOtp.respuestasOtp}). No lleva el código ni
     * nada derivado de él: un reintento tras un timeout usa la misma clave (security no cuenta otro intento) y un código
     * tecleado después de una respuesta usa {@code n + 1}. Puede salir en los logs.
     *
     * @param idNegociacion negociación
     * @param rutEmpresa    empresa del actor
     * @param rutOperador   operador
     * @param respuestas    {@code n}
     * @return la clave, dentro de la regex y el largo de security
     */
    public static String clave(final String idNegociacion, final String rutEmpresa, final String rutOperador,
            final int respuestas) {
        if (respuestas < 0) {
            throw new IllegalArgumentException("n de la clave de OTP negativo");
        }
        final String clave = String.join("|", parte(idNegociacion, "idNegociacion"), parte(rutEmpresa, "rutEmpresa"),
                parte(rutOperador, "rutOperador"), String.valueOf(respuestas));
        if (clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new IllegalArgumentException("clave de OTP de más de " + LARGO_MAXIMO_CLAVE + " caracteres");
        }
        return clave;
    }

    private static String parte(final String valor, final String nombre) {
        if (valor == null || !PARTE_SEGURA.matcher(valor).matches()) {
            throw new IllegalArgumentException(nombre + " vacío o con caracteres no admitidos en la clave de OTP");
        }
        return valor;
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
