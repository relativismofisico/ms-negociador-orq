package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.enums.EstadoFirma;

import java.util.List;

public interface FacturaPatchOutputPort {
    void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma);
    void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores);
    void actualizarEstadoFirmaPorNoFactura(String numeroFactura, EstadoFirma estadoFirma);
}
