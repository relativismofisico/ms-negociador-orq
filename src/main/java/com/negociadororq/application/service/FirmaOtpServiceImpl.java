package com.negociadororq.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.RolNoPermitidoException;
import com.negociadororq.application.port.output.AlertaFirmaOutputPort;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.domain.event.OtpValidatedEvent;
import com.negociadororq.domain.model.EstadoFirmaOtp;
import com.negociadororq.domain.model.PendingOtp;
import lombok.extern.java.Log;

/**
 * Implementación de {@link FirmaOtpService} (Fase 28c).
 *
 * <ul>
 *   <li>{@code valido=false} con motivo {@code INCORRECTO} e intentos restantes: la solicitud sigue en
 *       {@code PENDIENTE_OTP} (el usuario puede volver a teclear). Cualquier otro rechazo (expirado, bloqueado, usado o
 *       el formato anterior sin motivo) descarta la solicitud, como antes.</li>
 *   <li>{@code valido=true}: la solicitud pasa a {@code OTP_VALIDADO} con el código, la referencia, la fecha de
 *       validación y la IP <b>antes</b> de firmar, así que una caída a mitad de la firma la deja para el reconciliador.
 *       Al terminar, {@code FIRMADO} (y se borra el código de la solicitud: queda en el PDF).</li>
 *   <li>Fallo permanente (negociación o endoso inexistente) o reintentos agotados: {@code FALLO_FIRMA}, log
 *       {@code [FIRMA-FALLIDA]}, alerta {@code [ALERTA][FIRMA-FALLIDA]} y aviso a la empresa firmante. Fallo
 *       transitorio: queda en {@code OTP_VALIDADO} con el intento contado y lo reintenta el reconciliador.</li>
 *   <li>{@code ENDOSO_DUPLICADO} (el actor ya firmó con otro OTP): la solicitud se descarta, con log
 *       {@code [ENDOSO-DUPLICADO]} y su contador (sin datos del OTP), y sin aviso a la empresa.</li>
 *   <li>Duplicados de {@code otp.validate.response}: una solicitud ya FIRMADA o un evento sin solicitud activa se ignoran;
 *       la firma de cada factura es idempotente por {@code (otpId, factura)} en el endoso.</li>
 * </ul>
 */
@Log
public class FirmaOtpServiceImpl implements FirmaOtpService {

    static final String MOTIVO_INCORRECTO = "INCORRECTO";
    static final String ENDOSO_DUPLICADO = "ENDOSO_DUPLICADO";
    private static final int LARGO_MAXIMO_ERROR = 300;

    private final PendingOtpOutputPort pendingOtpPort;
    private final AprobacionInternaService aprobacion;
    private final NotificacionService notificaciones;
    private final AlertaFirmaOutputPort alerta;
    private final ClockPort clock;
    private final int maxIntentosFirma;
    private final Duration esperaReintento;

    /**
     * @param pendingOtpPort   solicitudes de firma por OTP
     * @param aprobacion       firma y aprobación del endoso
     * @param notificaciones   aviso a la empresa firmante de una firma fallida
     * @param alerta           métrica de firmas fallidas
     * @param clock            reloj
     * @param maxIntentosFirma intentos de firma (transitorios) antes de {@code FALLO_FIRMA}
     * @param esperaReintento  antigüedad mínima de una solicitud en {@code OTP_VALIDADO} para reintentarla
     */
    public FirmaOtpServiceImpl(final PendingOtpOutputPort pendingOtpPort, final AprobacionInternaService aprobacion,
            final NotificacionService notificaciones, final AlertaFirmaOutputPort alerta, final ClockPort clock,
            final int maxIntentosFirma, final Duration esperaReintento) {
        this.pendingOtpPort = pendingOtpPort;
        this.aprobacion = aprobacion;
        this.notificaciones = notificaciones;
        this.alerta = alerta;
        this.clock = clock;
        this.maxIntentosFirma = maxIntentosFirma;
        this.esperaReintento = esperaReintento;
    }

    @Override
    public void procesarValidacion(final OtpValidatedEvent event) {
        // Fase 28d (2.9): por la clave natural con la empresa y el operador del evento (respaldo por negociación solo si
        // hay una sola solicitud activa que encaje; con varias, [OTP-AMBIGUO] y no se firma).
        final Optional<PendingOtp> encontrada = pendingOtpPort.findByClave(event.getIdNegociacion(), event.getRutEmpresa(),
                event.getRutOperador());
        if (encontrada.isEmpty()) {
            log.info(String.format("[OTP-SIN-PENDIENTE] negociación %s otpId %s: no hay solicitud activa (duplicado o tardío), "
                    + "se ignora.", event.getIdNegociacion(), event.getOtpId()));
            return;
        }
        final PendingOtp pending = encontrada.get();
        if (event.getRutEmpresa() != null && pending.getRutEmpresa() != null
                && !event.getRutEmpresa().equals(pending.getRutEmpresa())) {
            log.warning(String.format("[OTP-OTRA-SOLICITUD] negociación %s: la validación es de otra empresa que la solicitud activa, "
                    + "se ignora.", event.getIdNegociacion()));
            return;
        }
        if (!event.isValido()) {
            rechazado(event, pending);
            return;
        }
        // Fase 28d (A1): la misma validación llega por HTTP y, después, por otp.validate.response (security la sigue
        // publicando en la transición), o el evento se reentrega. Con la solicitud ya terminada (FIRMADO, FALLO_FIRMA o
        // ROL_NO_PERMITIDO) no se vuelve a firmar: un fallo se repetiría con otra alerta y otro aviso a la empresa.
        if (!EstadoFirmaOtp.activa(pending.getEstado())) {
            log.info(String.format("[OTP-VALIDACION-REPETIDA] negociación %s otpId %s: la solicitud ya está en %s, se ignora.",
                    event.getIdNegociacion(), event.getOtpId(), pending.getEstado()));
            return;
        }
        if (EstadoFirmaOtp.OTP_VALIDADO.name().equals(pending.getEstado())
                && !Objects.equals(pending.getOtpId(), event.getOtpId())) {
            log.warning(String.format("[OTP-OTRA-SOLICITUD] negociación %s: ya hay una firma en curso con otro OTP, se ignora.",
                    event.getIdNegociacion()));
            return;
        }
        pending.setOtp(event.getOtp());
        pending.setIp(event.getIpAprobacion());
        pending.setOtpId(event.getOtpId());
        pending.setFechaValidacion(instanteValidacion(event));
        pending.setEstado(EstadoFirmaOtp.OTP_VALIDADO.name());
        pending.setFechaActualizacion(clock.now());
        firmar(pendingOtpPort.save(pending));
    }

    private void rechazado(final OtpValidatedEvent event, final PendingOtp pending) {
        if (!EstadoFirmaOtp.esperandoCodigo(pending.getEstado())) {
            log.info(String.format("[OTP-RECHAZO-TARDIO] negociación %s: la solicitud ya está en %s, se ignora el rechazo.",
                    event.getIdNegociacion(), pending.getEstado()));
            return;
        }
        final Integer restantes = event.getIntentosRestantes();
        if (MOTIVO_INCORRECTO.equals(event.getMotivo()) && restantes != null && restantes > 0) {
            log.info(String.format("[OTP-INCORRECTO] negociación %s: código incorrecto, quedan %s intento(s); la solicitud sigue "
                    + "pendiente.", event.getIdNegociacion(), restantes));
            return;
        }
        log.warning(String.format("OTP inválido para negociación %s (motivo %s): se descarta la solicitud.", event.getIdNegociacion(),
                event.getMotivo()));
        pendingOtpPort.deleteById(pending.getId());
    }

    @Override
    public int reintentarPendientes() {
        final LocalDateTime limite = clock.now().minus(esperaReintento);
        final List<PendingOtp> atascadas = pendingOtpPort.findByEstadoAndFechaActualizacionBefore(
                EstadoFirmaOtp.OTP_VALIDADO.name(), limite);
        for (PendingOtp pending : atascadas) {
            log.info(String.format("[FIRMA-REINTENTO] negociación %s otpId %s: intento %s de %s.", pending.getIdNegociacion(),
                    pending.getOtpId(), intentos(pending) + 1, maxIntentosFirma));
            firmar(pending);
        }
        return atascadas.size();
    }

    private void firmar(final PendingOtp pending) {
        try {
            aprobacion.aprobarInterno(pending);
        } catch (RolNoPermitidoException e) {
            pending.setEstado(EstadoFirmaOtp.ROL_NO_PERMITIDO.name());
            pending.setOtp(null);
            pending.setUltimoError(e.getCodigo());
            pending.setFechaActualizacion(clock.now());
            pendingOtpPort.save(pending);
            alerta.rolNoPermitido(pending.getIdNegociacion(), pending.getRutEmpresa(), e.getTipoOperacion(), e.getRol());
            return;
        } catch (ConflictoException e) {
            if (ENDOSO_DUPLICADO.equals(e.getCodigo())) {
                alerta.endosoDuplicado(pending.getIdNegociacion());
                pendingOtpPort.deleteById(pending.getId());
                return;
            }
            fallo(pending, e);
            return;
        } catch (RecursoNoEncontradoException e) {
            fallo(pending, e);
            return;
        } catch (RuntimeException e) {
            transitorio(pending, e);
            return;
        }
        pending.setEstado(EstadoFirmaOtp.FIRMADO.name());
        pending.setOtp(null);
        pending.setUltimoError(null);
        pending.setFechaActualizacion(clock.now());
        pendingOtpPort.save(pending);
    }

    private void transitorio(final PendingOtp pending, final RuntimeException e) {
        final int intentos = intentos(pending) + 1;
        pending.setIntentosFirma(intentos);
        pending.setUltimoError(resumen(e));
        pending.setFechaActualizacion(clock.now());
        if (intentos >= maxIntentosFirma) {
            fallo(pending, e);
            return;
        }
        log.warning(String.format("[FIRMA-REINTENTO] negociación %s otpId %s: la firma falló (%s), se reintenta (intento %s de %s).",
                pending.getIdNegociacion(), pending.getOtpId(), resumen(e), intentos, maxIntentosFirma));
        pendingOtpPort.save(pending);
    }

    private void fallo(final PendingOtp pending, final RuntimeException e) {
        pending.setEstado(EstadoFirmaOtp.FALLO_FIRMA.name());
        pending.setUltimoError(resumen(e));
        pending.setFechaActualizacion(clock.now());
        pendingOtpPort.save(pending);
        log.severe(String.format("[FIRMA-FALLIDA] negociación %s otpId %s empresa %s: la firma no se completó tras validar el OTP "
                        + "(%s). Requiere revisión manual.", pending.getIdNegociacion(), pending.getOtpId(),
                pending.getRutEmpresa(), resumen(e)));
        alerta.firmaFallida();
        try {
            notificaciones.notificarFirmaFallida(pending);
        } catch (RuntimeException aviso) {
            log.severe(String.format("[FIRMA-FALLIDA] negociación %s: no se pudo registrar el aviso a la empresa (%s).",
                    pending.getIdNegociacion(), resumen(aviso)));
        }
    }

    /**
     * Instante de la validación en security. Los eventos nuevos traen {@code instanteValidacion} (con zona); los del
     * formato anterior solo {@code fechaValidacion}, la hora local del servidor de security sin zona, que corre en UTC.
     */
    static Instant instanteValidacion(final OtpValidatedEvent event) {
        if (event.getInstanteValidacion() != null) {
            return event.getInstanteValidacion();
        }
        if (event.getFechaValidacion() != null) {
            return event.getFechaValidacion().toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    private static int intentos(final PendingOtp pending) {
        if (pending.getIntentosFirma() == null) {
            return 0;
        }
        return pending.getIntentosFirma();
    }

    private static String resumen(final RuntimeException e) {
        String texto = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (texto.length() > LARGO_MAXIMO_ERROR) {
            texto = texto.substring(0, LARGO_MAXIMO_ERROR);
        }
        return texto;
    }
}
