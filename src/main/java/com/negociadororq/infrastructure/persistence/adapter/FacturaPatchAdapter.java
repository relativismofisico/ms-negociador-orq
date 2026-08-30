package com.negociadororq.infrastructure.persistence.adapter;

import java.util.List;

import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link FacturaPatchOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class FacturaPatchAdapter implements FacturaPatchOutputPort {

    private final FacturaPatchRepository repository;

    @Override
    public void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion) {
        repository.actualizarEstadoPorId(facturaId, estadoFirma, estadoNegociacion);
    }

    @Override
    public void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores) {
        repository.marcarComoNegociada(facturaId, estadoFirma, actores);
    }

    @Override
    public void actualizarEstadoFirmaPorNoFactura(String numeroFactura, EstadoFirma estadoFirma) {
        repository.actualizarEstadoFirmaPorNoFactura(numeroFactura, estadoFirma);
    }

    @Override
    public void actualizarEstadosPorNoFactura(String numeroFactura, EstadoFirma estadoFirma,
                                               EstadoNegociacion estadoNegociacion) {
        repository.actualizarEstadosPorNoFactura(numeroFactura, estadoFirma, estadoNegociacion);
    }

    @Override
    public void actualizarNegociacionId(String numeroFactura, String negociacionId) {
        repository.actualizarNegociacionId(numeroFactura, negociacionId);
    }
}
