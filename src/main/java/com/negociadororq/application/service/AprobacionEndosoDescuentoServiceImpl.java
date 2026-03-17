package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.*;
import com.negociadororq.application.mapper.OperacionMapper;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.model.*;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.EstadoNegociacion;
import com.negociadororq.enums.RolActor;
import com.negociadororq.infrastructure.pdf.PdfFirmaService;
import com.negociadororq.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;



@Slf4j
@Service
@RequiredArgsConstructor
public class AprobacionEndosoDescuentoServiceImpl implements AprobacionEndosoDescuentoService {

    private final NegociacionRepository negociacionRepository;
    private final FacturaPatchRepository facturaPatchRepository;
    private  final EndosoPatchRepository endosoPatchRepository;
    private final OperacionMapper operacionMapper;
    private final OutboxService outboxService;

    @Lazy
    private final OperadorService operadorService;
    private final CompanyRepository companyRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;
    private final FacturaRepository facturaRepository;
    private final UserRepository userRepository;
    private final PendingOtpRepository pendingOtpRepository;
    private final EndosoRepository endosoRepository;
    private final PdfFirmaService pdfFirmaService;

    @Value("${topics.otp-request}")
    private String topicRequestOtp;

    @Value("${topics.otp-validate}")
    private String topicValidateOtp;

    @Value("${topics.liquidacion}")
    private String topicLiquidacionSolicitada;


    @Override
    public void solicitarOtp(AprobacionRequestDto request) {

        // Guardamos request temporal
        PendingOtp pending = PendingOtp.builder()
                .idNegociacion(request.getIdNegociacion())
                .rutEmpresa(request.getRutEmpresa())
                .rolActor(request.getRolActor())
                .request(request)
                .build();

        pendingOtpRepository.save(pending);

        // Publicamos evento
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
                        .build()
        );
    }

    @Transactional
    public AprobacionResponseDto aprobarInterno(AprobacionRequestDto request) {

        Negociacion negociacion = negociacionRepository.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        List<String> errores = new ArrayList<>();

        for (AprobacionFacturaRequestDto factReq : request.getFacturas()) {
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

            // 🔐 VALIDACIÓN: NO PERMITIR DOBLE ENDOSO
            boolean yaEndoso = factura.getAprobaciones().stream()
                    .anyMatch(a -> a.getRutActor().equals(request.getRutEmpresa()));

            if (yaEndoso) {
                errores.add("El actor " + request.getRutEmpresa() +
                        " ya realizó el endoso para la factura " +
                        factura.getNumeroFactura());
                throw new RuntimeException(
                        "El actor ya realizó el endoso para la factura " +
                                factura.getNumeroFactura()
                );
            }

            RolActor rolActor = resolverRolActor(request.getRutEmpresa(), factura);

            AprobacionActorDto aprobacion = new AprobacionActorDto(
                    request.getRutEmpresa(),
                    rolActor,
                    factReq.isAprobado(),
                    request.getObservacion(),
                    LocalDateTime.now()
            );

            // =========================
            // 🔥 FIRMA PDF
            // =========================
            Endoso endoso = endosoRepository
                    .findByNegociacionIdAndNumeroFactura(request.getIdNegociacion(), factura.getNumeroFactura())
                    .orElseThrow(() -> new RuntimeException("Endoso no encontrado"));

            byte[] pdfActual = endoso.getDocumentoPdf();

            String firma = "Firmado por: " + request.getRutEmpresa()
                    + " | Rol: " + rolActor
                    + " | Fecha: " + LocalDateTime.now();

            int indexFirma = factura.getAprobaciones().size();

            byte[] pdfFirmado = pdfFirmaService.agregarFirma(pdfActual, firma, indexFirma);

            endoso.setDocumentoPdf(new Binary(pdfFirmado).getData());

            endosoRepository.save(endoso);



            factura.getAprobaciones().removeIf(a -> a.getRutActor().equals(request.getRutEmpresa()));
            factura.getAprobaciones().add(aprobacion);

            actualizarEstadosFacturaEndosoNegociacion(negociacion.getId(), factura, negociacion.getTipoOperacion(), request.getRutOperador());
        }


        //Actualizamos los campos de la negociacion
        EstadoNegociacion  estadoGlobal = calcularEstadoNegociacion(negociacion);
        negociacion.setEstado(estadoGlobal.name());
        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionRepository.save(negociacion);

        AprobacionResponseDto response = new AprobacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setEstado(negociacion.getEstado());
        response.setFacturas(negociacion.getFacturas());
        response.setErrores(errores);
        return response;

    }

    private void actualizarEstadosFacturaEndosoNegociacion(String idNegociacion, FacturaNegociada factura, Operacion tipoOperacion, String rutOperador) {

        // 1. Si hay rechazo → RECHAZADA
        boolean hayRechazo = factura.getAprobaciones().stream().anyMatch(a -> !a.isAprobado());

        if (hayRechazo) {

            log.info("Reversando cupos por rechazo de factura {}", factura.getNumeroFactura());

            // Reversar cupos
            revertirCupos(factura, tipoOperacion, factura.getRutProveedor(), rutOperador);
            facturaPatchRepository.actualizarEstadoFirmaPorNoFactura(factura.getNumeroFactura(), EstadoFirma.RECHAZADA);
            endosoPatchRepository.actualizarEstadoEndosoPorNoFactura(idNegociacion, factura.getNumeroFactura(), EstadoEndoso.RECHAZADO);
            return;
        }

        // 2. Actores requeridos
        Set<RolActor> actoresRequeridos = obtenerActoresRequeridos(tipoOperacion.getTipo());

        // 3. Actores que aprobaron
        Set<RolActor> rolesQueAprobaron = factura.getAprobaciones().stream()
                .filter(AprobacionActorDto::isAprobado)
                .map(AprobacionActorDto::getRolActor)
                .collect(Collectors.toSet());

        boolean todosAprobaron = rolesQueAprobaron.containsAll(actoresRequeridos);
        boolean alguienAprobo = rolesQueAprobaron.stream().anyMatch(actoresRequeridos::contains);


        // 4. Estado final
        EstadoFirma estadoFactura;
        EstadoEndoso estadoEndoso;

        if (todosAprobaron) {
            estadoFactura = "FACTORING".equalsIgnoreCase(tipoOperacion.getTipo()) ? EstadoFirma.CESION : EstadoFirma.DESCUENTO;
            estadoEndoso = EstadoEndoso.ACEPTADO;

            OperacionRequest request = new OperacionRequest();
            request.setNumeroFactura(factura.getNumeroFactura());
            request.setOperacion(operacionMapper.toDto(tipoOperacion));

        } else if (alguienAprobo) {
            estadoFactura = EstadoFirma.EN_APROBACION;
            estadoEndoso = EstadoEndoso.PENDIENTE;
        } else {
            estadoFactura = EstadoFirma.EN_REVISION;
            estadoEndoso = EstadoEndoso.EN_REVISION;
        }

        // 5️⃣ PATCH ATÓMICO
        facturaPatchRepository.actualizarEstadoFirmaPorNoFactura(factura.getNumeroFactura(), estadoFactura);
        endosoPatchRepository.actualizarEstadoEndosoPorNoFactura(idNegociacion, factura.getNumeroFactura(), estadoEndoso);


        Operacion operacion = Operacion.builder()
                .tipo(tipoOperacion.getTipo())
                .clase(tipoOperacion.getClase())
                .build();

        LiquidacionSolicitadaEvent event =
                new LiquidacionSolicitadaEvent(
                        idNegociacion, factura.getNumeroFactura(),
                        operacion, LocalDateTime.now());

        outboxService.guardarEvento(idNegociacion, topicLiquidacionSolicitada, event);

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

        throw new RuntimeException("No se pudo determinar el rol del actor " + rutActor +
                " para la factura " + factura.getNumeroFactura());
    }

    private void revertirCupos(FacturaNegociada factura, Operacion tipoOperacion, String rutEmpresa, String rutOperador) {

        // Obtener factura completa
        Factura facturaDb = facturaRepository.findByNumeroFactura(factura.getNumeroFactura())
                .orElseThrow(() -> new RuntimeException("Factura no encontrada para reversión"));

        BigDecimal valorFactura = Optional.ofNullable(facturaDb.getValorNetoFactura())
                .orElse(facturaDb.getValorFactura());

        if (valorFactura == null) valorFactura = BigDecimal.ZERO;

        // 1️⃣ Usuario
        Long userId = userRepository.findByUsername(rutOperador)
                .map(User::getIdeUser)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        operadorService.revertirCupoUsuario(userId, valorFactura);

        // 2 Empresa
        Long companyId = companyRepository.findByNit(rutEmpresa)
                .map(Company::getIdeCompany)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        operadorService.revertirCupoEmpresa(companyId, valorFactura);


        // 3️⃣ Revertir cupo fondeadores
        final BigDecimal valorFinal = valorFactura;

        if (factura.getFondeadores() != null) {
            for (FondeadorAsignacion fon : factura.getFondeadores()) {

                Long funderId = companyRepository.findByNit(fon.getRutFondeador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException("Fondeador no encontrado"));

                Long payerId = companyRepository.findByNit(facturaDb.getRutPagador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));

                fondeadorQuotaRepository.findByFunderIdAndPayerId(funderId, payerId)
                        .ifPresent(fq -> {
                            fq.setPayerCurrentQuota(
                                    fq.getPayerCurrentQuota().add(valorFinal)
                            );
                        });
            }
        }
    }


}

