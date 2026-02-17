package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;
import com.negociadororq.application.controller.dto.OperacionRequest;
import com.negociadororq.application.mapper.OperacionMapper;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.EstadoNegociacion;
import com.negociadororq.enums.RolActor;
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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



    @Transactional
    @Override
    public AprobacionResponseDto aprobar(AprobacionRequestDto request) {
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
            RolActor rolActor = resolverRolActor(request.getRutEmpresa(), factura);

            AprobacionActorDto aprobacion = new AprobacionActorDto(
                    request.getRutEmpresa(),
                    rolActor,
                    factReq.isAprobado(),
                    request.getObservacion(),
                    LocalDateTime.now()
            );

            factura.getAprobaciones().removeIf(a -> a.getRutActor().equals(request.getRutEmpresa()));
            factura.getAprobaciones().add(aprobacion);

            actualizarEstadosFacturaEndosoNegociacion(negociacion.getId(), factura, negociacion.getTipoOperacion());
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

    private void actualizarEstadosFacturaEndosoNegociacion(String idNegociacion, FacturaNegociada factura, Operacion tipoOperacion) {

        // 1. Si hay rechazo → RECHAZADA
        boolean hayRechazo = factura.getAprobaciones().stream().anyMatch(a -> !a.isAprobado());

        if (hayRechazo) {
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
                        idNegociacion,
                        factura.getNumeroFactura(),
                        operacion,
                        LocalDateTime.now()
                );

        outboxService.guardarEvento(idNegociacion,
                "LIQUIDACION_SOLICITADA",
                event
        );

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

        if ("COMFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return Set.of(RolActor.PROVEEDOR, RolActor.FONDEADOR);
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

}

