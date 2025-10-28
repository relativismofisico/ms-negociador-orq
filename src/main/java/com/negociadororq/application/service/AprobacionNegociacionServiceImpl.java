package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.infrastructure.repository.NegociacionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class AprobacionNegociacionServiceImpl implements AprobacionNegociacionService {

    private final NegociacionRepository negociacionRepository;

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

            AprobacionActorDto aprobacion = new AprobacionActorDto(
                    request.getRutEmpresa(),
                    factReq.isAprobado(),
                    request.getObservacion(),
                    LocalDateTime.now()
            );

            factura.getAprobaciones().removeIf(a -> a.getRutActor().equals(request.getRutEmpresa()));
            factura.getAprobaciones().add(aprobacion);

            actualizarEstadoFactura(factura, negociacion.getTipoOperacion().getTipo());
        }

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

    private void actualizarEstadoFactura(FacturaNegociada factura, String tipoOperacion) {
        boolean algunRechazo = factura.getAprobaciones().stream().anyMatch(a -> !a.isAprobado());

        if (algunRechazo) {
            factura.setEstadoFirma(EstadoFirma.valueOf(EstadoFirma.RECHAZADA.name()));
            return;
        }

        boolean todosAprobaron = !factura.getAprobaciones().isEmpty() &&
                factura.getAprobaciones().stream().allMatch(AprobacionActorDto::isAprobado);

        if (todosAprobaron) {
            if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
                factura.setEstadoFirma(EstadoFirma.valueOf(EstadoFirma.CESION.name()));
            } else if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
                factura.setEstadoFirma(EstadoFirma.valueOf(EstadoFirma.DESCUENTO.name()));
            }
        } else {
            factura.setEstadoFirma(EstadoFirma.valueOf(EstadoFirma.EN_REVISION.name()));
        }
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
}

