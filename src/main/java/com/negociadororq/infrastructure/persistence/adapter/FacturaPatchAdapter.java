package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FacturaPatchAdapter implements FacturaPatchOutputPort {

    private final FacturaPatchRepository repository;

    @Override
    public void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma) {
        repository.actualizarEstadoPorId(facturaId, estadoFirma);
    }

    @Override
    public void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores) {
        repository.marcarComoNegociada(facturaId, estadoFirma, actores);
    }

    @Override
    public void actualizarEstadoFirmaPorNoFactura(String numeroFactura, EstadoFirma estadoFirma) {
        repository.actualizarEstadoFirmaPorNoFactura(numeroFactura, estadoFirma);
    }
}
