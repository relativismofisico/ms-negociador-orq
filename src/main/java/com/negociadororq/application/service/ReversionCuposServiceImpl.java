package com.negociadororq.application.service;

import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReversionCuposServiceImpl implements ReversionCuposService {

    private final NegociacionRepository negociacionRepository;

    private final FacturaRepository facturaRepository;

    private final FacturaPatchRepository facturaPatchRepository;

    private final EndosoPatchRepository endosoPatchRepository;

    private final AprobacionEndosoDescuentoServiceImpl aprobacionService;

    @Override
    public void revertirPorFactura(String numeroFactura, String actorId, String motivo) {

        Factura factura = facturaRepository.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada"));

        Negociacion negociacion = negociacionRepository.findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Negociación no encontrada"));

        FacturaNegociada facturaNegociada = negociacion.getFacturas()
                        .stream()
                        .filter(f ->
                                f.getNumeroFactura()
                                        .equals(numeroFactura))
                        .findFirst()
                        .orElseThrow();

        aprobacionService.revertirCupos(
                facturaNegociada, negociacion.getTipoOperacion(),
                facturaNegociada.getRutProveedor(), actorId);

        facturaPatchRepository
                .actualizarEstadoFirmaPorNoFactura(
                        numeroFactura, EstadoFirma.RECHAZADA);

        endosoPatchRepository
                .actualizarEstadoEndosoPorNoFactura(
                        negociacion.getId(),
                        numeroFactura, EstadoEndoso.RECHAZADO);

        log.info("Cupos revertidos por fallo de liquidación factura {} motivo {}", numeroFactura, motivo);
    }
}
