package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.infrastructure.persistence.repository.EndosoRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
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
    private final TemplateRenderService templateRenderService;
    private final FacturaPatchRepository facturaPatchRepository;
    private final DocumentoEndosoService documentoEndosoService;



    @Override
    @Transactional
    public EndosoResponseDto registrarEndoso(EndosoRequestDto request) {

        Negociacion negociacion = negociacionRepository.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        if (request.getNumerosFactura() == null || request.getNumerosFactura().isEmpty()) {
            throw new RuntimeException("Debe indicar al menos una factura");
        }

        List<String> creadas = new ArrayList<>();
        List<String> rechazadas = new ArrayList<>();


        String htmlPreview = null;

        for (String numeroFactura : request.getNumerosFactura()) {

            String tipoOperacion = negociacion.getTipoOperacion().getTipo();


            FacturaNegociada fn = negociacion.getFacturas().stream()
                    .filter(f ->
                            f.getNumeroFactura().equals(numeroFactura) &&
                                    esFacturaValidaParaOperacion(
                                            f,
                                            request.getRutActor(), // ⚠️ nombre del campo, pero representa el solicitante
                                            tipoOperacion
                                    )
                    )
                    .findFirst()
                    .orElse(null);

            if (fn == null) {
                String actor = "FACTORING".equalsIgnoreCase(tipoOperacion)
                        ? "PROVEEDOR"
                        : "PAGADOR";

                rechazadas.add(numeroFactura + " (no pertenece al " + actor + ")");
                continue;
            }

            // 🔹 Obtener la factura REAL desde Mongo
            Factura factura = facturaRepository.findByNumeroFactura(numeroFactura)
                    .orElse(null);

            if (factura == null) {
                rechazadas.add(numeroFactura + " (factura no existe en BD)");
                continue;
            }

            // 🔹 Evitar doble endoso
            boolean yaEndosada = endosoRepository.existsByNegociacionIdAndNumeroFactura(
                            request.getIdNegociacion(),
                            factura.getNumeroFactura());

            if (yaEndosada) {
                rechazadas.add(numeroFactura + " (ya tiene endoso)");
                continue;
            }


            Endoso endoso = Endoso.builder()
                    .negociacionId(request.getIdNegociacion())
                    .numeroFactura(factura.getNumeroFactura())
                    .rutProveedor(factura.getRutProveedor())
                    .rutPagador(fn.getRutPagador())
                    .rutFondeador(fn.getFondeadores().get(0).getRutFondeador())
                    .estado(EstadoEndoso.EN_REVISION)
                    .fechaCreacion(LocalDateTime.now())
                    .fechaActualizacion(LocalDateTime.now())
                    .build();

// 🔹 Generar documento formal antes de guardar
            documentoEndosoService.generarDocumentoFormal(
                    endoso,
                    negociacion,
                    factura,
                    fn
            );

            endosoRepository.save(endoso);
            creadas.add(numeroFactura);

            facturaPatchRepository.actualizarEstadoPorId(
                    factura.getId(),
                    EstadoFirma.EN_REVISION
            );

        }

        String estadoGlobal =
                creadas.isEmpty() ? "RECHAZADO"
                        : rechazadas.isEmpty() ? "PENDIENTE"
                        : "PARCIAL";

        return EndosoResponseDto.builder()
                .idNegociacion(request.getIdNegociacion())
                .estadoEndoso(estadoGlobal)
                .facturasEndosadas(creadas)
                .facturasRechazadas(rechazadas)
                .plantillaHtml(htmlPreview)
                .build();
    }


    private boolean esFacturaValidaParaOperacion(
            FacturaNegociada factura,
            String rutSolicitante,
            String tipoOperacion
    ) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutProveedor().equals(rutSolicitante);
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutPagador().equals(rutSolicitante);
        }

        return false;
    }

}
