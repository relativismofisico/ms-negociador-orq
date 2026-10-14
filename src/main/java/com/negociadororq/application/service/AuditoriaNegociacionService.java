package com.negociadororq.application.service;

import java.util.Objects;

import com.factoring.commons.auditoria.Acciones;
import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.factoring.commons.auditoria.AuditoriaAccionEventBuilder;
import com.factoring.commons.auditoria.AuditoriaActor;
import com.factoring.commons.auditoria.Canal;
import com.factoring.commons.auditoria.EntidadTipo;
import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.port.output.AuditoriaOutputPort;
import com.negociadororq.application.port.output.ContextoAuditoriaOutputPort;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.PendingOtp;
import lombok.RequiredArgsConstructor;

/**
 * Arma y registra las acciones auditadas de la negociación y el endoso.
 *
 * <p>Dos situaciones:</p>
 * <ul>
 *   <li>Request HTTP (crear negociación, registrar endoso, solicitar OTP): actor, IP y requestId salen de la request.</li>
 *   <li>Aprobación con OTP: corre en el listener del resultado del OTP, sin request. El actor y el requestId son los de
 *       la solicitud del OTP, guardados en {@link PendingOtp} ({@link #completarSolicitud}); la IP es la que el
 *       servidor resolvió al confirmar el OTP.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class AuditoriaNegociacionService {

    private final AuditoriaOutputPort auditoria;
    private final ContextoAuditoriaOutputPort contexto;

    /**
     * Guarda en la solicitud de OTP quién la hizo y en qué request, para auditar la aprobación cuando llegue el
     * resultado del OTP (en otro hilo, sin request).
     *
     * @param pending solicitud de OTP a completar
     */
    public void completarSolicitud(final PendingOtp pending) {
        final AuditoriaActor actor = contexto.actorActual();
        pending.setActorTipo(actor.tipo());
        pending.setActorId(actor.id());
        pending.setActorRol(actor.rol());
        pending.setActorEmpresaId(actor.empresaId());
        pending.setRequestId(contexto.requestIdActual());
    }

    /**
     * {@code NEGOCIACION_CREADA}.
     *
     * @param negociacion          negociación ya guardada (con id)
     * @param request              solicitud
     * @param facturasRechazadas   cantidad de facturas que no se negociaron
     */
    public void negociacionCreada(final Negociacion negociacion, final NegociacionRequestDto request,
            final int facturasRechazadas) {
        auditoria.registrar(AuditoriaAccionEvent.builder()
                .accion(Acciones.NEGOCIACION_CREADA)
                .entidad(EntidadTipo.NEGOCIACION, negociacion.getId())
                .actor(contexto.actorActual())
                .canal(Canal.WEB)
                .ip(contexto.ipActual())
                .estados(null, negociacion.getEstado())
                .detalle("rutEmpresa", request.getRutEmpresa())
                .detalle("rolEmpresa", request.getRolEmpresa())
                // Declarado en el cuerpo (el actor real es el del JWT): se conserva para cruzarlo.
                .detalle("rutOperadorDeclarado", request.getRutOperador())
                .detalle("tipoOperacion", tipoDe(negociacion))
                .detalle("totalNegociado", negociacion.getTotalNegociado())
                .detalle("facturasNegociadas", tamanio(negociacion))
                .detalle("facturasRechazadas", facturasRechazadas)
                .build());
    }

    /**
     * {@code ENDOSO_REGISTRADO}, uno por endoso creado.
     *
     * @param endoso  endoso ya guardado (con id)
     * @param request solicitud
     */
    public void endosoRegistrado(final Endoso endoso, final EndosoRequestDto request) {
        auditoria.registrar(AuditoriaAccionEvent.builder()
                .accion(Acciones.ENDOSO_REGISTRADO)
                .entidad(EntidadTipo.ENDOSO, endoso.getId())
                .actor(contexto.actorActual())
                .canal(Canal.WEB)
                .ip(contexto.ipActual())
                .estados(null, Objects.toString(endoso.getEstado(), null))
                .detalle("idNegociacion", endoso.getNegociacionId())
                .detalle("numerosFactura", endoso.getNumerosFactura())
                .detalle("rutFondeador", endoso.getRutFondeador())
                .detalle("rutActorEndosa", request.getRutActor())
                .detalle("rutOperadorDeclarado", request.getRutOperador())
                .build());
    }

    /**
     * {@code NEGOCIACION_FACTURA_APROBADA} / {@code NEGOCIACION_FACTURA_RECHAZADA}.
     *
     * @param pending       solicitud de OTP (actor, requestId e IP)
     * @param idNegociacion negociación
     * @param numeroFactura factura revisada
     * @param rolActor      rol de la empresa que revisó
     * @param aprobado      resultado
     */
    public void facturaRevisada(final PendingOtp pending, final String idNegociacion, final String numeroFactura,
            final RolActor rolActor, final boolean aprobado) {
        String accion = Acciones.NEGOCIACION_FACTURA_RECHAZADA;
        if (aprobado) {
            accion = Acciones.NEGOCIACION_FACTURA_APROBADA;
        }
        auditoria.registrar(desdeSolicitud(pending)
                .accion(accion)
                .entidad(EntidadTipo.NEGOCIACION, idNegociacion)
                .detalle("numeroFactura", numeroFactura)
                .detalle("rolActor", Objects.toString(rolActor, null))
                .detalle("rutEmpresa", pending.getRutEmpresa())
                .build());
    }

    /**
     * {@code NEGOCIACION_ESTADO_CAMBIADO}, solo si el estado global cambió.
     *
     * @param pending       solicitud de OTP
     * @param idNegociacion negociación
     * @param anterior      estado anterior
     * @param nuevo         estado nuevo
     */
    public void estadoNegociacion(final PendingOtp pending, final String idNegociacion, final String anterior,
            final String nuevo) {
        if (Objects.equals(anterior, nuevo)) {
            return;
        }
        auditoria.registrar(desdeSolicitud(pending)
                .accion(Acciones.NEGOCIACION_ESTADO_CAMBIADO)
                .entidad(EntidadTipo.NEGOCIACION, idNegociacion)
                .estados(anterior, nuevo)
                .build());
    }

    /**
     * {@code ENDOSO_FIRMADO}: la firma OTP quedó estampada en el documento del endoso.
     *
     * @param pending       solicitud de OTP
     * @param endoso        endoso firmado
     * @param numeroFactura factura de la firma
     * @param rolActor      rol de la empresa que firmó
     * @param aprobado      si la firma aprueba o rechaza
     */
    public void endosoFirmado(final PendingOtp pending, final Endoso endoso, final String numeroFactura,
            final RolActor rolActor, final boolean aprobado) {
        auditoria.registrar(desdeSolicitud(pending)
                .accion(Acciones.ENDOSO_FIRMADO)
                .entidad(EntidadTipo.ENDOSO, endoso.getId())
                .detalle("idNegociacion", endoso.getNegociacionId())
                .detalle("numeroFactura", numeroFactura)
                .detalle("rolActor", Objects.toString(rolActor, null))
                .detalle("aprobado", aprobado)
                .build());
    }

    /**
     * Actor de la aprobación: el usuario autenticado que pidió el OTP. Para solicitudes anteriores a este cambio (sin
     * actor guardado), el operador que declaró la solicitud, con la empresa por NIT.
     */
    static AuditoriaActor actorDe(final PendingOtp pending) {
        final AuditoriaActor actor;
        if (pending.getActorTipo() != null) {
            actor = new AuditoriaActor(pending.getActorTipo(), pending.getActorId(), pending.getActorRol(),
                    pending.getActorEmpresaId(), null);
        } else {
            actor = AuditoriaActor.usuario(pending.getRequest().getRutOperador(), null, null);
        }
        return actor.conEmpresaNit(pending.getRutEmpresa());
    }

    private static AuditoriaAccionEventBuilder desdeSolicitud(final PendingOtp pending) {
        return AuditoriaAccionEvent.builder()
                .actor(actorDe(pending))
                .canal(Canal.WEB)
                .ip(pending.getIp())
                .requestId(pending.getRequestId());
    }

    private static String tipoDe(final Negociacion negociacion) {
        if (negociacion.getTipoOperacion() == null) {
            return null;
        }
        return negociacion.getTipoOperacion().getTipo();
    }

    private static int tamanio(final Negociacion negociacion) {
        if (negociacion.getFacturas() == null) {
            return 0;
        }
        return negociacion.getFacturas().size();
    }
}
