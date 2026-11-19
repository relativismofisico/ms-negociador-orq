package com.negociadororq.application.service;

import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.domain.event.OtpValidatedEvent;
import com.negociadororq.domain.model.EstadoFirmaOtp;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.model.ValidacionOtp;

/**
 * Implementación de {@link ConfirmacionOtpService} (Fase 28d, A1).
 *
 * <ul>
 *   <li>Precondición (igual que en la 28c): sin un pendiente que espere el código, o de otra empresa, el código no se
 *       manda a validar ({@code 409 OTP_SIN_SOLICITUD_PENDIENTE}).</li>
 *   <li>El código va por HTTP a {@code POST /auth/internal/otp/validacion} con la clave
 *       {@code idNegociacion|rutEmpresa|rutOperador|n} ({@link ValidadorOtpPendiente}); no viaja por el outbox ni por
 *       Kafka. Es el único camino (A1b: retirados {@code otp.validate} y {@code otp.validate.response}).</li>
 *   <li>La respuesta definitiva de security entra en {@link FirmaOtpService#procesarValidacion}: incorrecto con
 *       intentos, rechazo, o firma.</li>
 *   <li>Security caído, timeout, 5xx o 503: la excepción sube antes de tocar el pendiente; no avanza {@code n} ni
 *       cambia el estado, y la firma se reintenta con la misma clave.</li>
 *   <li>El código no se escribe en ningún log.</li>
 * </ul>
 *
 * <p>{@link FirmaOtpService} sigue siendo idempotente por pendiente y {@code otpId} (dos confirmaciones simultáneas
 * del mismo actor firman una vez).</p>
 */
public class ConfirmacionOtpServiceImpl implements ConfirmacionOtpService {

    static final String OTP_SIN_SOLICITUD_PENDIENTE = "OTP_SIN_SOLICITUD_PENDIENTE";

    private final PendingOtpOutputPort pendingOtpPort;
    private final ValidadorOtpPendiente validador;
    private final FirmaOtpService firma;

    /**
     * @param pendingOtpPort pendientes OTP
     * @param validador      validación por HTTP con la clave de idempotencia
     * @param firma          firma con la respuesta de security
     */
    public ConfirmacionOtpServiceImpl(final PendingOtpOutputPort pendingOtpPort, final ValidadorOtpPendiente validador,
            final FirmaOtpService firma) {
        this.pendingOtpPort = pendingOtpPort;
        this.validador = validador;
        this.firma = firma;
    }

    @Override
    public void confirmarOtp(final ConfirmacionOtpRequestDto request) {
        final PendingOtp pending = pendingOtpPort.findByClave(request.getIdNegociacion(), request.getRutEmpresa(),
                        request.getRutOperador())
                .filter(p -> EstadoFirmaOtp.esperandoCodigo(p.getEstado()))
                .orElseThrow(() -> new ConflictoException(
                        "No hay una solicitud de OTP pendiente para la negociación " + request.getIdNegociacion(),
                        OTP_SIN_SOLICITUD_PENDIENTE));
        if (pending.getRutEmpresa() != null && !pending.getRutEmpresa().equals(request.getRutEmpresa())) {
            throw new ConflictoException("La solicitud de OTP pendiente es de otra empresa", OTP_SIN_SOLICITUD_PENDIENTE);
        }
        // Si security no responde de forma definitiva, la excepción sube aquí y nada cambia.
        final ValidacionOtp.Resultado resultado = validador.validar(pending, request.getOtp(), request.getIpAprobacion());
        firma.procesarValidacion(evento(pending, request, resultado));
    }

    /**
     * La respuesta de security como entrada de la firma. El código solo acompaña a una validación correcta (lo
     * estampa la firma en el PDF).
     */
    static OtpValidatedEvent evento(final PendingOtp pending, final ConfirmacionOtpRequestDto request,
            final ValidacionOtp.Resultado resultado) {
        String otp = null;
        if (resultado.valido()) {
            otp = request.getOtp();
        }
        return OtpValidatedEvent.builder()
                .idNegociacion(pending.getIdNegociacion())
                .rutEmpresa(pending.getRutEmpresa())
                .rutOperador(pending.getRutOperador())
                .valido(resultado.valido())
                .otp(otp)
                .ipAprobacion(request.getIpAprobacion())
                .otpId(resultado.otpId())
                .motivo(resultado.motivo())
                .intentosRestantes(resultado.intentosRestantes())
                .fechaValidacion(resultado.fechaValidacion())
                .instanteValidacion(resultado.instanteValidacion())
                .build();
    }
}
