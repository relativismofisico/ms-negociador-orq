package com.negociadororq.domain.model;

/**
 * Estado de una solicitud de firma por OTP (Fase 28c), guardado en {@code pending_otp}.
 *
 * <pre>
 * PENDIENTE_OTP --(otp.validate.response valido=true)--> OTP_VALIDADO --(firma estampada)--> FIRMADO
 *                                                              \--(fallo permanente o reintentos agotados)--> FALLO_FIRMA
 * </pre>
 *
 * <p>Un código incorrecto con intentos restantes deja la solicitud en {@code PENDIENTE_OTP}. Una solicitud sin estado
 * (creada antes de la Fase 28c) cuenta como {@code PENDIENTE_OTP}.</p>
 */
public enum EstadoFirmaOtp {
    PENDIENTE_OTP,
    OTP_VALIDADO,
    FIRMADO,
    FALLO_FIRMA,
    /** Fase 28d (2.10): el rol del actor no aprueba el tipo de operación; terminal, sin firma ni cupos tocados. */
    ROL_NO_PERMITIDO;

    /**
     * @param estado estado guardado (puede ser {@code null})
     * @return si la solicitud sigue viva (todavía no terminó en FIRMADO ni en FALLO_FIRMA)
     */
    public static boolean activa(final String estado) {
        return estado == null || PENDIENTE_OTP.name().equals(estado) || OTP_VALIDADO.name().equals(estado);
    }

    /**
     * @param estado estado guardado (puede ser {@code null})
     * @return si espera el código del usuario
     */
    public static boolean esperandoCodigo(final String estado) {
        return estado == null || PENDIENTE_OTP.name().equals(estado);
    }
}
