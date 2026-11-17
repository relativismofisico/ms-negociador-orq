package com.negociadororq.application.exception;

/**
 * Un servicio del que depende la operación (por ejemplo ms-security-util al validar el OTP) no respondió o respondió
 * con un error suyo: 503. La operación no cambió nada (Fase 28d, H b: el pendiente del OTP queda como estaba).
 */
public class ServicioDependienteNoDisponibleException extends NegociadorException {

    private static final long serialVersionUID = 1L;

    /** Código de error. */
    public static final String CODIGO = "SERVICIO_DEPENDIENTE_NO_DISPONIBLE";

    /**
     * @param mensaje texto para el cliente (sin detalles internos)
     */
    public ServicioDependienteNoDisponibleException(String mensaje) {
        super(mensaje, CODIGO);
    }
}
