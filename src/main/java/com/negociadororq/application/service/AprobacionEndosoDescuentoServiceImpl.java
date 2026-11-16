package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.negociadororq.application.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.AprobacionResponseDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.exception.RolNoPermitidoException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.ResultadoSolicitudOtp;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.AprobacionFacturaRequest;
import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.EstadoFirmaOtp;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link AprobacionEndosoDescuentoService} y {@link AprobacionInternaService}.
 */
@Log
@RequiredArgsConstructor
public class AprobacionEndosoDescuentoServiceImpl implements AprobacionEndosoDescuentoService, AprobacionInternaService {

    private final NegociacionOutputPort negociacionPort;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final EndosoPatchOutputPort endosoPatchPort;
    private final OutboxService outboxService;
    private final CupoReversionService cupoReversionService;
    private final CompanyOutputPort companyPort;
    private final FondeadorQuotaOutputPort fondeadorQuotaPort;
    private final FacturaOutputPort facturaPort;
    private final UserOutputPort userPort;
    private final PendingOtpOutputPort pendingOtpPort;
    private final FirmaEndosoService firmaEndosoService;
    private final NotificacionService notificacionService;
    private final EndosoOutputPort endosoPort;
    private final DocumentoEndosoService documentoEndosoService;
    private final ClockPort clock;
    private final String topicRequestOtp;
    private final String topicValidateOtp;
    private final String topicLiquidacionSolicitada;
    private final AuditoriaNegociacionService auditoria;

    /** Fase 28c: efectos que van al outbox viejo, marcados en la solicitud ({@link PendingOtp#getEfectosEmitidos()}). */
    static final String EFECTO_LIQUIDACION = "LIQUIDACION:";
    static final String EFECTO_LIQUIDACION_FALLIDA = "LIQUIDACION_FALLIDA:";
    static final String EFECTO_CORREO_FIRMA = "CORREO_FIRMA";
    /** Fase 28d: código de la firma única por empresa. */
    static final String CODIGO_FIRMA_EMPRESA_YA_REALIZADA = "FIRMA_EMPRESA_YA_REALIZADA";
    private static final java.util.concurrent.atomic.AtomicLong REEMPLAZOS = new java.util.concurrent.atomic.AtomicLong();

    @Override
    public void solicitarOtp(AprobacionRequestDto request) {

        // Fase 28d (2.10): el rol se resuelve en el servidor por el RUT sobre las facturas de la solicitud (el que
        // manda el cliente no es de fiar). Si no aprueba este tipo de operación, no se crea el pendiente ni se pide
        // el OTP.
        Negociacion negociacion = negociacionPort.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Negociación no encontrada: " + request.getIdNegociacion(), "NEGOCIACION_NO_ENCONTRADA"));
        List<String> numeros = Optional.ofNullable(request.getFacturas()).orElse(List.of()).stream()
                .map(AprobacionFacturaRequestDto::getNumeroFactura).toList();
        String rol = exigirRolPermitido(negociacion, request.getRutEmpresa(), numeros, true).name();
        if (request.getRolActor() != null && !rol.equalsIgnoreCase(request.getRolActor())) {
            log.warning(String.format("[ROL-CLIENTE-DISTINTO] negociación %s: el cliente envió el rol %s y el servidor "
                    + "resolvió %s; se usa el del servidor.", request.getIdNegociacion(), request.getRolActor(), rol));
        }

        AprobacionRequest domainRequest = mapToDomainRequest(request, rol);

        // Fase 28d (firma única): una firma por empresa. Si la empresa ya aprobó estas facturas, u otro operador suyo
        // tiene la firma en curso o ya firmó, no se pide otro OTP.
        if (empresaYaAprobo(negociacion, request.getRutEmpresa(), numeros)
                || pendingOtpPort.existeFirmaDeOtroOperador(request.getIdNegociacion(), request.getRutEmpresa(),
                        request.getRutOperador())) {
            throw new ConflictoException("Tu empresa ya firmó esta solicitud o tiene una firma en curso de otro usuario.",
                    CODIGO_FIRMA_EMPRESA_YA_REALIZADA);
        }

        PendingOtp pending = PendingOtp.builder()
                .idNegociacion(request.getIdNegociacion())
                .rutEmpresa(request.getRutEmpresa())
                .rutOperador(request.getRutOperador())
                .rolActor(rol)
                .request(domainRequest)
                // Si este método corre, el CompanyOwnershipGuard del controller ya
                // pasó (es la única vía de entrada): marca de trazabilidad que se
                // propaga hasta el evento de liquidación.
                .validadoEnOrigen(true)
                .estado(EstadoFirmaOtp.PENDIENTE_OTP.name())
                .fechaActualizacion(clock.now())
                .build();
        auditoria.completarSolicitud(pending);

        // Fase 28d (2.9): una sola solicitud por (negociación, empresa, operador), con alta atómica.
        ResultadoSolicitudOtp resultado = pendingOtpPort.registrarSolicitud(pending);
        switch (resultado.tipo()) {
            case FIRMA_EN_CURSO -> throw new ConflictoException("Ya hay una firma en curso para tu solicitud.",
                    "FIRMA_EN_CURSO");
            case YA_FIRMADA -> throw new ConflictoException(
                    "Tu empresa ya firmó esta solicitud o tiene una firma en curso de otro usuario.",
                    CODIGO_FIRMA_EMPRESA_YA_REALIZADA);
            case REEMPLAZADA -> log.warning(String.format("[OTP-REEMPLAZO] negociación %s: la solicitud anterior del mismo "
                    + "operador estaba en %s y se reemplaza por una nueva. Reemplazos desde el arranque: %d",
                    request.getIdNegociacion(), resultado.estadoAnterior(), REEMPLAZOS.incrementAndGet()));
            default -> {
                // NUEVA o REFRESCADA: se pide el OTP
            }
        }

        outboxService.guardarEvento(
                request.getIdNegociacion(),
                topicRequestOtp,
                OtpRequestEvent.builder()
                        .idNegociacion(request.getIdNegociacion())
                        .rutEmpresa(request.getRutEmpresa())
                        .rutOperador(request.getRutOperador())
                        .rolActor(rol)
                        .fechaSolicitud(clock.now())
                        .build()
        );
    }

    private AprobacionRequest mapToDomainRequest(AprobacionRequestDto dto, String rol) {
        List<AprobacionFacturaRequest> facturas;
        if (dto.getFacturas() == null) {
            facturas = new ArrayList<>();
        } else {
            facturas = dto.getFacturas().stream()
                    .map(f -> new AprobacionFacturaRequest(f.getNumeroFactura(), f.isAprobado(), f.getObservacion()))
                    .collect(Collectors.toList());
        }

        return new AprobacionRequest(
                dto.getIdNegociacion(),
                dto.getRutOperador(),
                dto.getRutEmpresa(),
                rol,
                facturas,
                dto.getObservacion()
        );
    }

    @Override
    public void confirmarOtp(ConfirmacionOtpRequestDto request) {
        // Fase 28c: precondición. Sin una solicitud que espere el código (o de otra empresa) no se manda a validar:
        // el código no viaja si no hay nada que firmar.
        PendingOtp pending = pendingOtpPort.findByClave(request.getIdNegociacion(), request.getRutEmpresa(),
                        request.getRutOperador())
                .filter(p -> EstadoFirmaOtp.esperandoCodigo(p.getEstado()))
                .orElseThrow(() -> new ConflictoException(
                        "No hay una solicitud de OTP pendiente para la negociación " + request.getIdNegociacion(),
                        "OTP_SIN_SOLICITUD_PENDIENTE"));
        if (pending.getRutEmpresa() != null && !pending.getRutEmpresa().equals(request.getRutEmpresa())) {
            throw new ConflictoException("La solicitud de OTP pendiente es de otra empresa",
                    "OTP_SIN_SOLICITUD_PENDIENTE");
        }
        outboxService.guardarEvento(
                request.getIdNegociacion(),
                topicValidateOtp,
                OtpValidateRequestEvent.builder()
                        .idNegociacion(request.getIdNegociacion())
                        .rutEmpresa(request.getRutEmpresa())
                        .rutOperador(request.getRutOperador())
                        .otp(request.getOtp())
                        .ipAprobacion(request.getIpAprobacion())
                        .build()
        );
    }

    @Override
    @Transactional
    public AprobacionResponseDto aprobarInterno(PendingOtp pending) {

        AprobacionRequest request = pending.getRequest();

        Negociacion negociacion = negociacionPort.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Negociación no encontrada: " + request.getIdNegociacion(), "NEGOCIACION_NO_ENCONTRADA"));

        // Fase 28d (2.10): misma regla que en solicitarOtp, antes de estampar firmas o registrar aprobaciones (p. ej.
        // pendientes creados antes del cambio). El llamador deja el pendiente en ROL_NO_PERMITIDO sin tocar cupos.
        // Las facturas que no están en la negociación siguen como antes: se reportan como error en la respuesta.
        exigirRolPermitido(negociacion, request.getRutEmpresa(),
                request.getFacturas().stream().map(AprobacionFacturaRequest::getNumeroFactura).toList(), false);

        List<String> errores = new ArrayList<>();
        int yaAplicadas = 0;

        for (AprobacionFacturaRequest factReq : request.getFacturas()) {
            Optional<FacturaNegociada> optFactura = negociacion.getFacturas().stream()
                    .filter(f -> f.getNumeroFactura().equals(factReq.getNumeroFactura()))
                    .findFirst();

            if (optFactura.isEmpty()) {
                errores.add("Factura no encontrada en la negociación: " + factReq.getNumeroFactura());
                continue;
            }

            FacturaNegociada factura = optFactura.get();

            if (factura.getAprobaciones() == null) {
                factura.setAprobaciones(new ArrayList<>());
            }

            Optional<AprobacionActorDto> previa = factura.getAprobaciones().stream()
                    .filter(a -> a.getRutActor().equals(request.getRutEmpresa()))
                    .findFirst();

            // Fase 28c: la misma validación (mismo otpId) ya aplicada no se aplica otra vez; con otro OTP, conflicto.
            if (previa.isPresent() && pending.getOtpId() != null
                    && pending.getOtpId().equals(previa.get().getOtpId())) {
                yaAplicadas++;
                continue;
            }

            if (previa.isPresent()) {
                throw new ConflictoException(
                        "El actor " + request.getRutEmpresa()
                        + " ya realizó el endoso para la factura " + factura.getNumeroFactura(),
                        "ENDOSO_DUPLICADO");
            }

            RolActor rolActor = resolverRolActor(request.getRutEmpresa(), factura);

            AprobacionActorDto aprobacion = new AprobacionActorDto(
                    request.getRutEmpresa(), rolActor, factReq.isAprobado(),
                    request.getObservacion(), clock.now());
            aprobacion.setOtpId(pending.getOtpId());

            // En Confirming, el firmante del documento (usuario de la empresa
            // proveedora) recién se conoce en este punto: quien solicitó el
            // descuento fue el Pagador. Antes de estampar la firma OTP, se
            // regenera el documento completo con los datos del Proveedor que
            // está aprobando.
            // Fase 28c: si el documento ya tiene alguna firma estampada (de otro actor, o la propia en un reintento),
            // NO se regenera: el documento nuevo no tendría esos bloques (solo existen dentro del PDF) y se perderían.
            // En ese caso el documento conserva el cuerpo anterior y la firma del Proveedor se estampa debajo.
            if ("CONFIRMING".equalsIgnoreCase(negociacion.getTipoOperacion().getTipo())
                    && rolActor == RolActor.PROVEEDOR
                    && factReq.isAprobado()
                    && !firmaEndosoService.tieneFirmas(negociacion.getId(), factura.getNumeroFactura())) {
                regenerarDocumentoConFirmante(
                        negociacion, negociacion.getId(), factura.getNumeroFactura(), request.getRutOperador());
            }

            // Fase 28d (firma única): reclamo atómico de la firma de la empresa sobre esta factura; otro OTP de la
            // misma empresa (otro operador, a la vez) no estampa un segundo bloque.
            String otpFirma = Optional.ofNullable(pending.getOtpId()).orElse(pending.getId());
            if (!endosoPatchPort.reclamarFirmaEmpresa(request.getIdNegociacion(), factura.getNumeroFactura(),
                    request.getRutEmpresa(), otpFirma)) {
                throw new ConflictoException(
                        "El actor " + request.getRutEmpresa() + " ya realizó el endoso para la factura "
                                + factura.getNumeroFactura(), "ENDOSO_DUPLICADO");
            }

            firmaEndosoService.firmarPdfEndoso(
                    request.getIdNegociacion(), factura, pending, request.getRutEmpresa(),
                    request.getRutOperador(), rolActor, factReq.isAprobado(), request.getObservacion());
            auditoria.facturaRevisada(pending, negociacion.getId(), factura.getNumeroFactura(), rolActor,
                    factReq.isAprobado());

            factura.getAprobaciones().removeIf(a -> a.getRutActor().equals(request.getRutEmpresa()));
            factura.getAprobaciones().add(aprobacion);
            actualizarAprobacionEndoso(negociacion.getId(), factura, aprobacion);

            actualizarEstadosFacturaEndosoNegociacion(
                    negociacion, negociacion.getId(), factura,
                    negociacion.getTipoOperacion(), request.getRutOperador(), pending);
        }

        if (yaAplicadas > 0 && yaAplicadas == request.getFacturas().size()) {
            // Reentrega de una validación ya aplicada entera: sin efectos (ni correo, ni estado, ni eventos).
            AprobacionResponseDto repetida = new AprobacionResponseDto();
            repetida.setIdNegociacion(negociacion.getId());
            repetida.setEstado(negociacion.getEstado());
            repetida.setFacturas(negociacion.getFacturas());
            repetida.setErrores(errores);
            return repetida;
        }

        if (!yaEmitido(pending, EFECTO_CORREO_FIRMA)) {
            notificacionService.notificarFirmaEndoso(request);
            marcarEmitido(pending, EFECTO_CORREO_FIRMA);
        }

        EstadoNegociacion estadoGlobal = calcularEstadoNegociacion(negociacion);
        String estadoAnterior = negociacion.getEstado();
        negociacion.setEstado(estadoGlobal.name());
        negociacion.setFechaUltimaActualizacion(clock.now());
        negociacionPort.save(negociacion);
        auditoria.estadoNegociacion(pending, negociacion.getId(), estadoAnterior, negociacion.getEstado());

        AprobacionResponseDto response = new AprobacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setEstado(negociacion.getEstado());
        response.setFacturas(negociacion.getFacturas());
        response.setErrores(errores);
        return response;
    }

    private void actualizarEstadosFacturaEndosoNegociacion(Negociacion negociacion,
                                                           String idNegociacion,
                                                           FacturaNegociada factura,
                                                           Operacion tipoOperacion,
                                                           String rutOperador,
                                                           PendingOtp pending) {

        Set<RolActor> actoresRequeridos = obtenerActoresRequeridos(tipoOperacion.getTipo());

        boolean hayRechazoEnActoresRequeridos = factura.getAprobaciones().stream()
                .filter(a -> actoresRequeridos.contains(a.getRolActor()))
                .anyMatch(a -> !a.isAprobado());

        if (hayRechazoEnActoresRequeridos) {
            log.info(String.format("Reversando cupos por rechazo de factura %s", factura.getNumeroFactura()));
            String motivoError = "La factura " + factura.getNumeroFactura()
                    + " fue rechazada durante el proceso de aprobación/endoso";

            revertirCupos(factura, tipoOperacion, factura.getRutProveedor(), rutOperador);
            String efectoFallida = EFECTO_LIQUIDACION_FALLIDA + factura.getNumeroFactura();
            if (!yaEmitido(pending, efectoFallida)) {
                notificacionService.notificarLiquidacionFallida(negociacion, factura.getNumeroFactura(), motivoError);
                marcarEmitido(pending, efectoFallida);
            }
            facturaPatchPort.actualizarEstadosPorNoFactura(
                    factura.getNumeroFactura(), EstadoFirma.RECHAZADA, EstadoNegociacion.RECHAZADA);
            factura.setEstadoFirma(EstadoFirma.RECHAZADA);
            factura.setEstadoNegociacion(EstadoNegociacion.RECHAZADA);
            actualizarEstadoEndosoGrupal(negociacion, idNegociacion, factura.getNumeroFactura());
            return;
        }

        Set<RolActor> rolesQueAprobaron = factura.getAprobaciones().stream()
                .filter(AprobacionActorDto::isAprobado)
                .map(AprobacionActorDto::getRolActor)
                .collect(Collectors.toSet());

        boolean todosAprobaron = rolesQueAprobaron.containsAll(actoresRequeridos);
        boolean alguienAprobo = rolesQueAprobaron.stream().anyMatch(actoresRequeridos::contains);

        EstadoFirma estadoFactura;
        EstadoNegociacion estadoNegociacionFactura;

        if (todosAprobaron) {
            if ("FACTORING".equalsIgnoreCase(tipoOperacion.getTipo())) {
                estadoFactura = EstadoFirma.CESION;
            } else {
                estadoFactura = EstadoFirma.DESCUENTO;
            }
            estadoNegociacionFactura = EstadoNegociacion.APROBADA;

            Operacion operacion = Operacion.builder()
                    .tipo(tipoOperacion.getTipo())
                    .clase(tipoOperacion.getClase())
                    .build();

            String efectoLiquidacion = EFECTO_LIQUIDACION + factura.getNumeroFactura();
            if (yaEmitido(pending, efectoLiquidacion)) {
                log.info(String.format("[LIQUIDACION-YA-SOLICITADA] negociación %s factura %s: la solicitud de liquidación ya "
                        + "se escribió en un intento anterior de esta aprobación; no se repite.", idNegociacion,
                        factura.getNumeroFactura()));
            } else {
                LiquidacionSolicitadaEvent event = new LiquidacionSolicitadaEvent(
                        idNegociacion, rutOperador, factura.getNumeroFactura(), operacion, clock.now(),
                        Boolean.TRUE.equals(pending.getValidadoEnOrigen()), pending.getRequestId());

                outboxService.guardarEvento(idNegociacion, topicLiquidacionSolicitada, event);
                marcarEmitido(pending, efectoLiquidacion);
            }
        } else if (alguienAprobo) {
            estadoFactura = EstadoFirma.EN_APROBACION;
            estadoNegociacionFactura = EstadoNegociacion.EN_APROBACION;
        } else {
            estadoFactura = EstadoFirma.EN_REVISION;
            estadoNegociacionFactura = EstadoNegociacion.PENDIENTE;
        }

        facturaPatchPort.actualizarEstadosPorNoFactura(factura.getNumeroFactura(), estadoFactura, estadoNegociacionFactura);

        // También se actualiza la copia embebida en la negociación: calcularEstadoNegociacion()
        // (llamado justo después, para cada factura de la solicitud) lee este mismo campo para
        // determinar el estado global de la negociación, así que si no se sincroniza acá queda
        // desactualizado y el estado de la negociación nunca refleja lo que en verdad pasó.
        factura.setEstadoFirma(estadoFactura);
        factura.setEstadoNegociacion(estadoNegociacionFactura);

        actualizarEstadoEndosoGrupal(negociacion, idNegociacion, factura.getNumeroFactura());
    }

    /**
     * El Endoso que cubre esta factura puede agrupar varias facturas (fondeador con
     * endosoGrupal=true): en vez de sobreescribir su "estado" con el resultado de
     * solo esta factura, se recalcula a partir del estadoFirma actual (ya
     * actualizado) de TODAS las facturas que ese Endoso cubre — mismo criterio que
     * calcularEstadoNegociacion(), pero acotado a las facturas del Endoso en vez de
     * a toda la negociación.
     */
    private void actualizarEstadoEndosoGrupal(Negociacion negociacion, String idNegociacion, String numeroFactura) {
        Endoso endoso = endosoPort.findByNegociacionIdAndNumeroFactura(idNegociacion, numeroFactura).orElse(null);
        if (endoso == null) {
            return;
        }

        Set<String> numerosFacturaEndoso = Set.copyOf(
                Optional.ofNullable(endoso.getNumerosFactura()).orElse(List.of()));

        List<EstadoFirma> estadosFirma = negociacion.getFacturas().stream()
                .filter(f -> numerosFacturaEndoso.contains(f.getNumeroFactura()))
                .map(FacturaNegociada::getEstadoFirma)
                .toList();

        boolean hayPendientes = estadosFirma.stream().anyMatch(e ->
                e == EstadoFirma.EN_APROBACION || e == EstadoFirma.EN_REVISION
                        || e == EstadoFirma.SIN_CESION || e == EstadoFirma.SIN_DESCUENTO);
        boolean hayAprobadas = estadosFirma.stream().anyMatch(e -> e == EstadoFirma.CESION || e == EstadoFirma.DESCUENTO);
        boolean hayRechazadas = estadosFirma.stream().anyMatch(e -> e == EstadoFirma.RECHAZADA);

        EstadoEndoso estadoGrupal;
        if (hayPendientes) {
            estadoGrupal = EstadoEndoso.EN_REVISION;
        } else if (hayAprobadas && hayRechazadas) {
            estadoGrupal = EstadoEndoso.PARCIAL;
        } else if (hayAprobadas) {
            estadoGrupal = EstadoEndoso.ACEPTADO;
        } else if (hayRechazadas) {
            estadoGrupal = EstadoEndoso.RECHAZADO;
        } else {
            estadoGrupal = EstadoEndoso.EN_REVISION;
        }

        endosoPatchPort.actualizarEstadoEndosoPorNoFactura(idNegociacion, numeroFactura, estadoGrupal);
    }

    /**
     * Regenera el documento formal del endoso que cubre esta factura, ahora con
     * los datos del usuario que lo firma. Solo aplica a Confirming (ver llamador):
     * en Factoring el firmante ya se conoce desde la solicitud.
     */
    private void regenerarDocumentoConFirmante(Negociacion negociacion, String idNegociacion,
                                                String numeroFactura, String rutOperadorFirmante) {
        Endoso endoso = endosoPort.findByNegociacionIdAndNumeroFactura(idNegociacion, numeroFactura).orElse(null);
        if (endoso == null) {
            return;
        }

        List<String> numerosFacturaEndoso = Optional.ofNullable(endoso.getNumerosFactura()).orElse(List.of());

        List<Factura> facturas = numerosFacturaEndoso.stream()
                .map(nf -> facturaPort.findByNumeroFactura(nf).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        List<FacturaNegociada> facturasNegociadas = negociacion.getFacturas().stream()
                .filter(fn -> numerosFacturaEndoso.contains(fn.getNumeroFactura()))
                .toList();

        if (facturas.isEmpty() || facturasNegociadas.isEmpty()) {
            log.warning("No se pudo regenerar el documento del endoso " + endoso.getId()
                    + ": no se encontraron las facturas cubiertas (" + numerosFacturaEndoso + ")");
            return;
        }

        documentoEndosoService.generarDocumentoFormal(
                endoso, negociacion, facturas, facturasNegociadas, rutOperadorFirmante);
        endosoPort.save(endoso);
    }

    private EstadoNegociacion calcularEstadoNegociacion(Negociacion negociacion) {
        List<FacturaNegociada> facturas = negociacion.getFacturas();

        // SIN_CESION/SIN_DESCUENTO es el estado inicial de una factura a la que
        // todavía no se le solicitó endoso/descuento: cuenta como "sin resolver"
        // igual que EN_APROBACION/EN_REVISION, para no dar por aprobada/rechazada
        // la negociación completa mientras alguna de sus facturas ni siquiera
        // entró al proceso.
        boolean hayPendientes = facturas.stream().anyMatch(f ->
                f.getEstadoFirma() == EstadoFirma.EN_APROBACION
                        || f.getEstadoFirma() == EstadoFirma.EN_REVISION
                        || f.getEstadoFirma() == EstadoFirma.SIN_CESION
                        || f.getEstadoFirma() == EstadoFirma.SIN_DESCUENTO
        );
        boolean hayAprobadas = facturas.stream().anyMatch(f ->
                f.getEstadoFirma() == EstadoFirma.CESION
                        || f.getEstadoFirma() == EstadoFirma.DESCUENTO
        );
        boolean hayRechazadas = facturas.stream().anyMatch(f ->
                f.getEstadoFirma() == EstadoFirma.RECHAZADA
        );

        if (hayPendientes) {
            return EstadoNegociacion.EN_APROBACION;
        }
        if (hayAprobadas && hayRechazadas) {
            return EstadoNegociacion.PARCIAL;
        }
        if (hayAprobadas) {
            return EstadoNegociacion.APROBADA;
        }
        if (hayRechazadas) {
            return EstadoNegociacion.RECHAZADA;
        }
        return EstadoNegociacion.EN_APROBACION;
    }

    private Set<RolActor> obtenerActoresRequeridos(String tipoOperacion) {
        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return Set.of(RolActor.PAGADOR, RolActor.FONDEADOR);
        }
        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return Set.of(RolActor.PROVEEDOR);
        }
        return Set.of();
    }

    /**
     * Fase 28d (2.10): el rol real del actor en las facturas indicadas (o, si no se indica ninguna, en las de la
     * negociación donde participa) debe ser de los que aprueban el tipo de operación.
     *
     * @param negociacion    negociación
     * @param rutEmpresa     RUT de la empresa que aprueba
     * @param numerosFactura facturas de la solicitud
     * @param exigirParticipacion si ninguna factura de la solicitud está en la negociación: {@code true} lanza
     *                            ROL_ACTOR_INDETERMINADO; {@code false} no comprueba nada (devuelve {@code null})
     * @return el rol resuelto
     * @throws RolNoPermitidoException si el rol no aprueba ese tipo de operación
     * @throws ReglaDeNegocioException ROL_ACTOR_INDETERMINADO si el actor no participa en las facturas
     */
    RolActor exigirRolPermitido(Negociacion negociacion, String rutEmpresa, List<String> numerosFactura,
                                boolean exigirParticipacion) {
        String tipo = negociacion.getTipoOperacion().getTipo();
        List<FacturaNegociada> facturas = Optional.ofNullable(negociacion.getFacturas()).orElse(List.of());
        List<RolActor> roles = new ArrayList<>();
        if (numerosFactura.isEmpty()) {
            for (FacturaNegociada f : facturas) {
                rolEn(rutEmpresa, f).ifPresent(roles::add);
            }
        } else {
            for (FacturaNegociada f : facturas) {
                if (numerosFactura.contains(f.getNumeroFactura())) {
                    roles.add(resolverRolActor(rutEmpresa, f));
                }
            }
        }
        if (roles.isEmpty() && !exigirParticipacion && !numerosFactura.isEmpty()) {
            return null;
        }
        if (roles.isEmpty()) {
            throw new ReglaDeNegocioException("No se pudo determinar el rol del actor " + rutEmpresa
                    + " en la negociación " + negociacion.getId(), "ROL_ACTOR_INDETERMINADO");
        }
        Set<RolActor> requeridos = obtenerActoresRequeridos(tipo);
        for (RolActor rol : roles) {
            if (!requeridos.contains(rol)) {
                throw new RolNoPermitidoException(tipo, rol.name());
            }
        }
        return roles.get(0);
    }

    private static boolean empresaYaAprobo(Negociacion negociacion, String rutEmpresa, List<String> numerosFactura) {
        return Optional.ofNullable(negociacion.getFacturas()).orElse(List.of()).stream()
                .filter(f -> numerosFactura.isEmpty() || numerosFactura.contains(f.getNumeroFactura()))
                .flatMap(f -> Optional.ofNullable(f.getAprobaciones()).orElse(List.of()).stream())
                .anyMatch(a -> rutEmpresa.equals(a.getRutActor()));
    }

    private static Optional<RolActor> rolEn(String rutActor, FacturaNegociada factura) {
        if (rutActor.equals(factura.getRutPagador())) {
            return Optional.of(RolActor.PAGADOR);
        }
        if (rutActor.equals(factura.getRutProveedor())) {
            return Optional.of(RolActor.PROVEEDOR);
        }
        boolean esFondeador = Optional.ofNullable(factura.getFondeadores()).orElse(Collections.emptyList()).stream()
                .anyMatch(f -> f.getRutFondeador().equals(rutActor));
        if (esFondeador) {
            return Optional.of(RolActor.FONDEADOR);
        }
        return Optional.empty();
    }

    private RolActor resolverRolActor(String rutActor, FacturaNegociada factura) {
        if (rutActor.equals(factura.getRutPagador())) {
            return RolActor.PAGADOR;
        }
        if (rutActor.equals(factura.getRutProveedor())) {
            return RolActor.PROVEEDOR;
        }
        boolean esFondeador = Optional.ofNullable(factura.getFondeadores())
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(f -> f.getRutFondeador().equals(rutActor));

        if (esFondeador) {
            return RolActor.FONDEADOR;
        }

        throw new ReglaDeNegocioException(
                "No se pudo determinar el rol del actor " + rutActor
                + " para la factura " + factura.getNumeroFactura(),
                "ROL_ACTOR_INDETERMINADO");
    }

    /**
     * Revierte el cupo consumido (usuario, empresa y cada fondeador asignado) por una factura
     * que fue rechazada durante el proceso de aprobación/endoso.
     *
     * @param factura       factura negociada rechazada, con sus fondeadores asignados
     * @param tipoOperacion FACTORING o CONFIRMING, para reversar el cupo del lado correcto
     * @param rutEmpresa    RUT de la empresa cuyo cupo se revierte
     * @param rutOperador   username del operador cuyo cupo individual se revierte
     */
    @Transactional
    public void revertirCupos(FacturaNegociada factura, Operacion tipoOperacion, String rutEmpresa, String rutOperador) {

        Factura facturaDb = facturaPort.findByNumeroFactura(factura.getNumeroFactura())
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura no encontrada para reversión"));

        BigDecimal valorFactura = Optional.ofNullable(facturaDb.getValorNetoFactura())
                .orElse(facturaDb.getValorFactura());

        if (valorFactura == null) {
            valorFactura = BigDecimal.ZERO;
        }

        Long userId = userPort.findByUsername(rutOperador)
                .map(User::getIdeUser)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        cupoReversionService.revertirCupoUsuario(userId, valorFactura);

        Long companyId = companyPort.findByNit(rutEmpresa)
                .map(Company::getIdeCompany)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));

        cupoReversionService.revertirCupoEmpresa(companyId, valorFactura);

        if (factura.getFondeadores() != null && !factura.getFondeadores().isEmpty()) {
            for (FondeadorAsignacion fon : factura.getFondeadores()) {
                Long funderId = companyPort.findByNit(fon.getRutFondeador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Fondeador no encontrado: " + fon.getRutFondeador()));

                if ("FACTORING".equalsIgnoreCase(tipoOperacion.getTipo())) {
                    Long supplierId = companyPort.findByNit(facturaDb.getRutProveedor())
                            .map(Company::getIdeCompany)
                            .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado"));
                    cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valorFactura);
                }

                if ("CONFIRMING".equalsIgnoreCase(tipoOperacion.getTipo())) {
                    Long payerId = companyPort.findByNit(facturaDb.getRutPagador())
                            .map(Company::getIdeCompany)
                            .orElseThrow(() -> new RecursoNoEncontradoException("Pagador no encontrado"));
                    cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valorFactura);
                }
            }
        }
    }

    /**
     * Fase 28c: el outbox viejo (Mongo) no participa de la transacción JPA de {@link #aprobarInterno}: si un intento
     * falla después de escribir un evento, el reintento lo escribiría otra vez. Cada efecto se marca en la solicitud
     * justo después de escribirlo, y un reintento de la misma solicitud lo salta.
     */
    private static boolean yaEmitido(PendingOtp pending, String efecto) {
        return pending.getEfectosEmitidos() != null && pending.getEfectosEmitidos().contains(efecto);
    }

    private void marcarEmitido(PendingOtp pending, String efecto) {
        List<String> efectos = new ArrayList<>();
        if (pending.getEfectosEmitidos() != null) {
            efectos.addAll(pending.getEfectosEmitidos());
        }
        efectos.add(efecto);
        pending.setEfectosEmitidos(efectos);
        pendingOtpPort.save(pending);
    }

    private void actualizarAprobacionEndoso(String idNegociacion, FacturaNegociada factura, AprobacionActorDto aprobacion) {
        EstadoEndoso estado;
        if (aprobacion.isAprobado()) {
            estado = EstadoEndoso.PENDIENTE;
        } else {
            estado = EstadoEndoso.RECHAZADO;
        }
        endosoPatchPort.registrarAprobacionActor(idNegociacion, factura.getNumeroFactura(), aprobacion, estado);
    }
}
