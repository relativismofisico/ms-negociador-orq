package com.negociadororq.application.service;

import java.util.logging.Logger;

import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.ValidacionOtpOutputPort;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.model.ValidacionOtp;

/**
 * Fase 28d (H): valida por HTTP el código de un pendiente OTP con la clave de idempotencia
 * {@code idNegociacion|rutEmpresa|rutOperador|n} ({@link ValidacionOtp#clave}).
 *
 * <ul>
 *   <li>{@code n} es {@code PendingOtp.respuestasOtp}: solo avanza (CAS) cuando security responde de forma definitiva
 *       (cualquier resultado, válido o no).</li>
 *   <li>Security caído, timeout o 5xx: la excepción de {@link ValidacionOtpOutputPort} sube sin avanzar {@code n}; el
 *       reintento del mismo intento reusa la clave y security no cuenta otro intento.</li>
 *   <li>Misma clave con otro código: security no lo valida y devuelve el estado actual del OTP (el intento de esa
 *       clave ya se contó); {@code n} avanza y el siguiente código usa otra clave.</li>
 *   <li>El código no se escribe en ningún log; la clave sí.</li>
 * </ul>
 *
 * <p>El pendiente no se borra mientras pueda volver a pedirse el OTP de la misma negociación, empresa y operador: un
 * pendiente nuevo empezaría en {@code n = 0} y reusaría claves ya vistas por security (que respondería con el estado
 * actual, nunca con una aprobación).</p>
 */
public class ValidadorOtpPendiente {

    private static final Logger LOG = Logger.getLogger(ValidadorOtpPendiente.class.getName());

    private final ValidacionOtpOutputPort validacion;
    private final PendingOtpOutputPort pendientes;

    /**
     * @param validacion cliente HTTP de security
     * @param pendientes pendientes OTP
     */
    public ValidadorOtpPendiente(final ValidacionOtpOutputPort validacion, final PendingOtpOutputPort pendientes) {
        this.validacion = validacion;
        this.pendientes = pendientes;
    }

    /**
     * @param pendiente    pendiente del actor (con negociación, empresa y operador)
     * @param otp          código tecleado
     * @param ipAprobacion IP del actor
     * @return el resultado de security
     */
    public ValidacionOtp.Resultado validar(final PendingOtp pendiente, final String otp, final String ipAprobacion) {
        final int vistas = respuestas(pendiente);
        final String clave = ValidacionOtp.clave(pendiente.getIdNegociacion(), pendiente.getRutEmpresa(),
                pendiente.getRutOperador(), vistas);
        // Si security no responde de forma definitiva, la excepción sube aquí y n queda igual.
        final ValidacionOtp.Resultado resultado = validacion.validar(new ValidacionOtp.Solicitud(
                pendiente.getIdNegociacion(), pendiente.getRutEmpresa(), pendiente.getRutOperador(), otp, ipAprobacion,
                clave));
        if (!pendientes.registrarRespuestaOtp(pendiente.getId(), vistas)) {
            LOG.info(() -> "[OTP-HTTP] clave " + clave + ": otro intento ya contó esta respuesta");
        }
        pendiente.setRespuestasOtp(Math.max(respuestas(pendiente), vistas + 1));
        LOG.info(() -> "[OTP-HTTP] clave " + clave + ": valido=" + resultado.valido() + " motivo=" + resultado.motivo()
                + " intentosRestantes=" + resultado.intentosRestantes());
        return resultado;
    }

    private static int respuestas(final PendingOtp pendiente) {
        if (pendiente.getRespuestasOtp() == null) {
            return 0;
        }
        return pendiente.getRespuestasOtp();
    }
}
