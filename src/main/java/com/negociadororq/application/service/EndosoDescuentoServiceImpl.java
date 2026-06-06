package com.negociadororq.application.service;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Log
public class EndosoDescuentoServiceImpl implements EndosoDescuentoService {

    private final NegociacionOutputPort negociacionPort;
    private final EndosoOutputPort endosoPort;
    private final FacturaOutputPort facturaPort;
    private final NotificacionService notificacionService;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final DocumentoEndosoService documentoEndosoService;
    private final NegociacionPatchOutputPort negociacionPatchPort;



    @Override
    @Transactional
    public EndosoResponseDto registrarEndoso(EndosoRequestDto request) {

        Negociacion negociacion = negociacionPort.findById(request.getIdNegociacion())
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

            Factura factura = facturaPort.findByNumeroFactura(numeroFactura)
                    .orElse(null);

            if (factura == null) {
                facturasRechazadas.add(numeroFactura + " (factura no existe en BD)");
                continue;
            }

            boolean facturaYaEndosada = endosoPort.existsByNegociacionIdAndNumeroFactura(
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

            documentoEndosoService.generarDocumentoFormal(endoso, negociacion, factura, facturaNegociada);

            endosoPort.save(endoso);

            notificacionService.notificarEndoso(endoso);

            facturasCreadas.add(numeroFactura);

            facturaPatchPort.actualizarEstadoPorId(factura.getId(), EstadoFirma.EN_REVISION);

            negociacionPatchPort.actualizarEstadoEndosoPorId(negociacion.getId(), factura.getNumeroFactura(), true);

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

