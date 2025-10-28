package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.infrastructure.repository.EndosoRepository;
import com.negociadororq.infrastructure.repository.FacturaRepository;
import com.negociadororq.infrastructure.repository.NegociacionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class EndosoServiceImpl implements EndosoService {

    private final NegociacionRepository negociacionRepository;
    private final EndosoRepository endosoRepository;
    private final FacturaRepository facturaRepository;

    @Override
    @Transactional
    public EndosoResponseDto registrarEndoso(EndosoRequestDto request) {
        Negociacion negociacion = negociacionRepository.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        // Buscar facturas del proveedor dentro de la negociación
        List<FacturaNegociada> facturasProveedor = negociacion.getFacturas().stream()
                .filter(f -> f.getRutProveedor().equals(request.getRutProveedor()))
                .toList();

        if (facturasProveedor.isEmpty()) {
            throw new RuntimeException("El proveedor no tiene facturas asociadas en esta negociación");
        }

        for (FacturaNegociada factura : facturasProveedor) {
            Endoso endoso = Endoso.builder()
                    .negociacionId(request.getIdNegociacion())
                    //.numeroFactura(factura.getNumeroFactura())
                    .rutProveedor(request.getRutProveedor())
                    .rutFondeador(factura.getFondeadores().get(0).getRutFondeador()) // primer fondeador
                    .estado(request.isEndosado() ? EstadoEndoso.ACEPTADO : EstadoEndoso.RECHAZADO)
                    .fechaCreacion(LocalDateTime.now())
                    .fechaActualizacion(LocalDateTime.now())
                    .build();

            endosoRepository.save(endoso);

            // actualizar estadoFirma de la factura si se endosa
            if (request.isEndosado()) {
                facturaRepository.findByNumeroFactura(factura.getNumeroFactura())
                        .ifPresent(f -> {
                            f.setEstadoFirma("ENDOSADA");
                            facturaRepository.save(f);
                        });
            }
        }

        String estado = request.isEndosado() ? "ENDOSADA" : "RECHAZADA";

        return EndosoResponseDto.builder()
                .idNegociacion(request.getIdNegociacion())
                .estadoEndoso(estado)
                .build();
    }

    @Override
    public EndosoResponseDto aprobarEndoso(String idEndoso, String rol, boolean aprobado) {
        return null;
    }


}
