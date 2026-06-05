package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.infrastructure.persistence.repository.*;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class EndosoDescuentoServiceImpl implements EndosoDescuentoService {

    private final NegociacionRepository negociacionRepository;
    private final EndosoRepository endosoRepository;
    private final FacturaRepository facturaRepository;
    private final NotificacionService notificacionService;
    private final FacturaPatchRepository facturaPatchRepository;
    private final DocumentoEndosoService documentoEndosoService;
    private final NegociacionPatchRepository negociacionPatchRepository;;



    @Override
    @Transactional
    public EndosoResponseDto registrarEndoso(EndosoRequestDto request) {

        Negociacion negociacion = negociacionRepository.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Negociación no encontrada: " + request.getIdNegociacion(), "NEGOCIACION_NO_ENCONTRADA"));

        if (request.getNumerosFactura() == null || request.getNumerosFactura().isEmpty()) {
            throw new PeticionInvalidaException("Debe indicar al menos una factura", "FACTURAS_REQUERIDAS");
        }

        List<String> facturasCreadas = new ArrayList<>();
        List<String> facturasRechazadas = new ArrayList<>();



        for (String numeroFactura : request.getNumerosFactura()) {

            String tipoOperacion = negociacion.getTipoOperacion().getTipo();


            FacturaNegociada facturaNegociada = negociacion.getFacturas().stream()
                    .filter(facturaNegociadaPivote ->
                            facturaNegociadaPivote.getNumeroFactura().equals(numeroFactura) &&
                                    esFacturaValidaParaOperacion(
                                            facturaNegociadaPivote,
                                            request.getRutActor(),
                                            tipoOperacion
                                    )
                    )
                    .findFirst()
                    .orElse(null);

            if (facturaNegociada == null) {
                String actor = "FACTORING".equalsIgnoreCase(tipoOperacion)
                        ? "PROVEEDOR"
                        : "PAGADOR";

                facturasRechazadas.add(numeroFactura + " (no pertenece al " + actor + ")");
                continue;
            }

            // 🔹 Obtener la factura REAL desde Mongo
            Factura factura = facturaRepository.findByNumeroFactura(numeroFactura)
                    .orElse(null);

            if (factura == null) {
                facturasRechazadas.add(numeroFactura + " (factura no existe en BD)");
                continue;
            }

            // 🔹 Evitar doble endoso
            boolean facturaYaEndosada = endosoRepository.existsByNegociacionIdAndNumeroFactura(
                            request.getIdNegociacion(),
                            factura.getNumeroFactura());

            if (facturaYaEndosada) {
                facturasRechazadas.add(numeroFactura + " (ya tiene endoso)");
                continue;
            }


            Endoso endoso = Endoso.builder()
                    .negociacionId(request.getIdNegociacion())
                    .numeroFactura(factura.getNumeroFactura())
                    .rutActorEndosa(request.getRutActor())
                    .rutProveedor(factura.getRutProveedor())
                    .tipoOperacion(negociacion.getTipoOperacion())
                    .rutPagador(facturaNegociada.getRutPagador())
                    .rutFondeador(facturaNegociada.getFondeadores().get(0).getRutFondeador())
                    .estado(EstadoEndoso.EN_REVISION)
                    .fechaCreacion(LocalDateTime.now())
                    .fechaActualizacion(LocalDateTime.now())
                    .build();

            // 🔹 Generar documento formal antes de guardar
            documentoEndosoService.generarDocumentoFormal(endoso, negociacion, factura, facturaNegociada);

            endosoRepository.save(endoso);

            notificacionService.notificarEndoso(endoso);

            facturasCreadas.add(numeroFactura);

            facturaPatchRepository.actualizarEstadoPorId(factura.getId(), EstadoFirma.EN_REVISION);

            negociacionPatchRepository.actualizarEstadoEndosoPorId(negociacion.getId(), factura.getNumeroFactura(), true);

        }

        String estadoGlobal =
                facturasCreadas.isEmpty() ? "RECHAZADO"
                        : facturasRechazadas.isEmpty() ? "PENDIENTE"
                        : "PARCIAL";

        return EndosoResponseDto.builder()
                .idNegociacion(request.getIdNegociacion())
                .estadoEndoso(estadoGlobal)
                .facturasEndosadas(facturasCreadas)
                .facturasRechazadas(facturasRechazadas)
                .build();
    }


    private boolean esFacturaValidaParaOperacion(FacturaNegociada factura, String rutSolicitante, String tipoOperacion) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutProveedor().equals(rutSolicitante);
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutPagador().equals(rutSolicitante);
        }

        return false;
    }

}
