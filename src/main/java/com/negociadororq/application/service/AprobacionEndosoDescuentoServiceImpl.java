package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.RolActor;
import com.negociadororq.infrastructure.repository.EndosoPatchRepository;
import com.negociadororq.infrastructure.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.repository.NegociacionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.negociadororq.enums.RolActor;



@Slf4j
@Service
@RequiredArgsConstructor
public class AprobacionEndosoDescuentoServiceImpl implements AprobacionEndosoDescuentoService {

    private final NegociacionRepository negociacionRepository;
    private final FacturaPatchRepository facturaPatchRepository;
    private  final EndosoPatchRepository endosoPatchRepository;

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

            actualizarEstadosFacturaEndosoNegociacion(negociacion.getId(), factura, negociacion.getTipoOperacion().getTipo());
        }

        //Actualizamos los campos de la negociacion
        String estadoGlobal = calcularEstadoNegociacion(negociacion);
        negociacion.setEstado(estadoGlobal);
        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionRepository.save(negociacion);

        AprobacionResponseDto response = new AprobacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setEstado(negociacion.getEstado());
        response.setFacturas(negociacion.getFacturas()); // aquí ya cuadra porque es List<FacturaNegociada>
        response.setErrores(errores);
        return response;

    }

    private void actualizarEstadosFacturaEndosoNegociacion(String idNegociacion, FacturaNegociada factura, String tipoOperacion) {

        // 1. Si hay rechazo → RECHAZADA
        boolean hayRechazo = factura.getAprobaciones().stream().anyMatch(a -> !a.isAprobado());

        if (hayRechazo) {
            facturaPatchRepository.actualizarEstadoFirmaPorNoFactura(factura.getNumeroFactura(), EstadoFirma.RECHAZADA);
            endosoPatchRepository.actualizarEstadoEndosoPorNoFactura(idNegociacion, factura.getNumeroFactura(), EstadoEndoso.RECHAZADO);
            return;
        }

        // 2. Actores requeridos
        Set<RolActor> actoresRequeridos = obtenerActoresRequeridos(tipoOperacion);

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
            estadoFactura = "FACTORING".equalsIgnoreCase(tipoOperacion)
                    ? EstadoFirma.CESION
                    : EstadoFirma.DESCUENTO;
            estadoEndoso = EstadoEndoso.ACEPTADO;
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

    }


    private String calcularEstadoNegociacion(Negociacion negociacion) {
        if (negociacion.getFacturas().stream().anyMatch(f -> EstadoFirma.RECHAZADA.name().equals(f.getEstadoFirma()))) {
            return EstadoFirma.RECHAZADA.name();
        }
        if (negociacion.getFacturas().stream().allMatch(f -> EstadoFirma.CESION.name().equals(f.getEstadoFirma())
                || EstadoFirma.DESCUENTO.name().equals(f.getEstadoFirma()))) {
            return EstadoFirma.APROBACION.name();
        }
        return EstadoFirma.EN_APROBACION.name();
    }

    private Set<RolActor> obtenerActoresRequeridos(String tipoOperacion) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return Set.of(RolActor.PAGADOR, RolActor.FONDEADOR);
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
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

