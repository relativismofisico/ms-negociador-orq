package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;

import java.util.List;

public interface FacturaPatchOutputPort {
    void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion);
    void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores);
    void actualizarEstadoFirmaPorNoFactura(String numeroFactura, EstadoFirma estadoFirma);

    /**
     * Actualiza tanto estadoFirma como estadoNegociacion de la factura. A diferencia de
     * {@link #actualizarEstadoFirmaPorNoFactura}, que solo tocaba estadoFirma, este método
     * mantiene ambos campos sincronizados durante el ciclo de vida del endoso (creación,
     * aprobación parcial, aprobación total y rechazo).
     *
     * @param numeroFactura número de la factura
     * @param estadoFirma nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación
     */
    void actualizarEstadosPorNoFactura(String numeroFactura, EstadoFirma estadoFirma,
                                        EstadoNegociacion estadoNegociacion);

    /**
     * Deja registrado en la factura a qué negociación pertenece, para poder
     * sub-agrupar facturas liquidadas por negociación (Facturas liquidadas).
     */
    void actualizarNegociacionId(String numeroFactura, String negociacionId);
}
