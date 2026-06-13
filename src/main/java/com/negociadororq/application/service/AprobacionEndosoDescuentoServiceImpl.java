package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.AprobacionResponseDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.AprobacionFacturaRequest;
import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.model.User;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.enums.RolActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Log
@Service
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

    @Value("${topics.otp-request}")
    private String topicRequestOtp;

    @Value("${topics.otp-validate}")
    private String topicValidateOtp;

    @Value("${topics.liquidacion}")
    private String topicLiquidacionSolicitada;

    @Override
    public void solicitarOtp(AprobacionRequestDto request) {

        AprobacionRequest domainRequest = mapToDomainRequest(request);

        PendingOtp pending = PendingOtp.builder()
                .idNegociacion(request.getIdNegociacion())
                .rutEmpresa(request.getRutEmpresa())
                .rolActor(request.getRolActor())
                .request(domainRequest)
                .build();

        pendingOtpPort.save(pending);

        outboxService.guardarEvento(
                request.getIdNegociacion(),
                topicRequestOtp,
                OtpRequestEvent.builder()
                        .idNegociacion(request.getIdNegociacion())
                        .rutEmpresa(request.getRutEmpresa())
                        .rutOperador(request.getRutOperador())
                        .rolActor(request.getRolActor())
                        .fechaSolicitud(LocalDateTime.now())
                        .build()
        );
    }

    private AprobacionRequest mapToDomainRequest(AprobacionRequestDto dto) {
        List<AprobacionFacturaRequest> facturas = dto.getFacturas() == null
                ? new ArrayList<>()
                : dto.getFacturas().stream()
                        .map(f -> new AprobacionFacturaRequest(f.getNumeroFactura(), f.isAprobado(), f.getObservacion()))
                        .collect(Collectors.toList());

        return new AprobacionRequest(
                dto.getIdNegociacion(),
                dto.getRutOperador(),
                dto.getRutEmpresa(),
                dto.getRolActor(),
                facturas,
                dto.getObservacion()
        );
    }

    @Override
    public void confirmarOtp(ConfirmacionOtpRequestDto request) {
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

    @Transactional
    public AprobacionResponseDto aprobarInterno(PendingOtp pending) {

        AprobacionRequest request = pending.getRequest();

        Negociacion negociacion = negociacionPort.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Negociación no encontrada: " + request.getIdNegociacion(), "NEGOCIACION_NO_ENCONTRADA"));

        List<String> errores = new ArrayList<>();

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

            boolean yaEndoso = factura.getAprobaciones().stream()
                    .anyMatch(a -> a.getRutActor().equals(request.getRutEmpresa()));

            if (yaEndoso) {
                throw new ConflictoException(
                        "El actor " + request.getRutEmpresa() +
                        " ya realizó el endoso para la factura " + factura.getNumeroFactura(),
                        "ENDOSO_DUPLICADO");
            }

            RolActor rolActor = resolverRolActor(request.getRutEmpresa(), factura);

            AprobacionActorDto aprobacion = new AprobacionActorDto(
                    request.getRutEmpresa(), rolActor, factReq.isAprobado(),
                    request.getObservacion(), LocalDateTime.now());

            firmaEndosoService.firmarPdfEndoso(
                    request.getIdNegociacion(), factura, pending, request.getRutEmpresa(),
                    request.getRutOperador(), rolActor, factReq.isAprobado(), request.getObservacion());

            factura.getAprobaciones().removeIf(a -> a.getRutActor().equals(request.getRutEmpresa()));
            factura.getAprobaciones().add(aprobacion);
            actualizarAprobacionEndoso(negociacion.getId(), factura, aprobacion);

            actualizarEstadosFacturaEndosoNegociacion(
                    negociacion, negociacion.getId(), factura,
                    negociacion.getTipoOperacion(), request.getRutOperador());
        }

        notificacionService.notificarFirmaEndoso(request);

        EstadoNegociacion estadoGlobal = calcularEstadoNegociacion(negociacion);
        negociacion.setEstado(estadoGlobal.name());
        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionPort.save(negociacion);

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
                                                           String rutOperador) {

        Set<RolActor> actoresRequeridos = obtenerActoresRequeridos(tipoOperacion.getTipo());

        boolean hayRechazoEnActoresRequeridos = factura.getAprobaciones().stream()
                .filter(a -> actoresRequeridos.contains(a.getRolActor()))
                .anyMatch(a -> !a.isAprobado());

        if (hayRechazoEnActoresRequeridos) {
            log.info(String.format("Reversando cupos por rechazo de factura %s", factura.getNumeroFactura()));
            String motivoError = "La factura " + factura.getNumeroFactura()
                    + " fue rechazada durante el proceso de aprobación/endoso";

            revertirCupos(factura, tipoOperacion, factura.getRutProveedor(), rutOperador);
            notificacionService.notificarLiquidacionFallida(negociacion, motivoError);
            facturaPatchPort.actualizarEstadoFirmaPorNoFactura(factura.getNumeroFactura(), EstadoFirma.RECHAZADA);
            endosoPatchPort.actualizarEstadoEndosoPorNoFactura(idNegociacion, factura.getNumeroFactura(), EstadoEndoso.RECHAZADO);
            return;
        }

        Set<RolActor> rolesQueAprobaron = factura.getAprobaciones().stream()
                .filter(AprobacionActorDto::isAprobado)
                .map(AprobacionActorDto::getRolActor)
                .collect(Collectors.toSet());

        boolean todosAprobaron = rolesQueAprobaron.containsAll(actoresRequeridos);
        boolean alguienAprobo = rolesQueAprobaron.stream().anyMatch(actoresRequeridos::contains);

        EstadoFirma estadoFactura;
        EstadoEndoso estadoEndoso;

        if (todosAprobaron) {
            estadoFactura = "FACTORING".equalsIgnoreCase(tipoOperacion.getTipo())
                    ? EstadoFirma.CESION : EstadoFirma.DESCUENTO;
            estadoEndoso = EstadoEndoso.ACEPTADO;

            Operacion operacion = Operacion.builder()
                    .tipo(tipoOperacion.getTipo())
                    .clase(tipoOperacion.getClase())
                    .build();

            LiquidacionSolicitadaEvent event = new LiquidacionSolicitadaEvent(
                    idNegociacion, rutOperador, factura.getNumeroFactura(), operacion, LocalDateTime.now());

            outboxService.guardarEvento(idNegociacion, topicLiquidacionSolicitada, event);
        } else if (alguienAprobo) {
            estadoFactura = EstadoFirma.EN_APROBACION;
            estadoEndoso = EstadoEndoso.PENDIENTE;
        } else {
            estadoFactura = EstadoFirma.EN_REVISION;
            estadoEndoso = EstadoEndoso.EN_REVISION;
        }

        facturaPatchPort.actualizarEstadoFirmaPorNoFactura(factura.getNumeroFactura(), estadoFactura);
        endosoPatchPort.actualizarEstadoEndosoPorNoFactura(idNegociacion, factura.getNumeroFactura(), estadoEndoso);
    }

    private EstadoNegociacion calcularEstadoNegociacion(Negociacion negociacion) {
        List<FacturaNegociada> facturas = negociacion.getFacturas();

        boolean hayPendientes = facturas.stream().anyMatch(f ->
                EstadoFirma.EN_APROBACION.name().equals(f.getEstadoFirma()) ||
                        EstadoFirma.EN_REVISION.name().equals(f.getEstadoFirma())
        );
        boolean hayAprobadas = facturas.stream().anyMatch(f ->
                EstadoFirma.CESION.name().equals(f.getEstadoFirma()) ||
                        EstadoFirma.DESCUENTO.name().equals(f.getEstadoFirma())
        );
        boolean hayRechazadas = facturas.stream().anyMatch(f ->
                EstadoFirma.RECHAZADA.name().equals(f.getEstadoFirma())
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
                "No se pudo determinar el rol del actor " + rutActor +
                " para la factura " + factura.getNumeroFactura(),
                "ROL_ACTOR_INDETERMINADO");
    }

    @Transactional
    public void revertirCupos(FacturaNegociada factura, Operacion tipoOperacion, String rutEmpresa, String rutOperador) {

        Factura facturaDb = facturaPort.findByNumeroFactura(factura.getNumeroFactura())
                .orElseThrow(() -> new RuntimeException("Factura no encontrada para reversión"));

        BigDecimal valorFactura = Optional.ofNullable(facturaDb.getValorNetoFactura())
                .orElse(facturaDb.getValorFactura());

        if (valorFactura == null) {
            valorFactura = BigDecimal.ZERO;
        }

        Long userId = userPort.findByUsername(rutOperador)
                .map(User::getIdeUser)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        cupoReversionService.revertirCupoUsuario(userId, valorFactura);

        Long companyId = companyPort.findByNit(rutEmpresa)
                .map(Company::getIdeCompany)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        cupoReversionService.revertirCupoEmpresa(companyId, valorFactura);

        if (factura.getFondeadores() != null && !factura.getFondeadores().isEmpty()) {
            for (FondeadorAsignacion fon : factura.getFondeadores()) {
                Long funderId = companyPort.findByNit(fon.getRutFondeador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException("Fondeador no encontrado: " + fon.getRutFondeador()));

                if ("FACTORING".equalsIgnoreCase(tipoOperacion.getTipo())) {
                    Long supplierId = companyPort.findByNit(facturaDb.getRutProveedor())
                            .map(Company::getIdeCompany)
                            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
                    cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valorFactura);
                }

                if ("CONFIRMING".equalsIgnoreCase(tipoOperacion.getTipo())) {
                    Long payerId = companyPort.findByNit(facturaDb.getRutPagador())
                            .map(Company::getIdeCompany)
                            .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));
                    cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valorFactura);
                }
            }
        }
    }

    private void actualizarAprobacionEndoso(String idNegociacion, FacturaNegociada factura, AprobacionActorDto aprobacion) {
        EstadoEndoso estado = aprobacion.isAprobado() ? EstadoEndoso.PENDIENTE : EstadoEndoso.RECHAZADO;
        endosoPatchPort.registrarAprobacionActor(idNegociacion, factura.getNumeroFactura(), aprobacion, estado);
    }
}
