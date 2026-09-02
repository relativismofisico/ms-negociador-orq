package com.negociadororq.application.service.notificacion.impl;

import com.negociadororq.application.service.notificacion.CuerpoNotificacionBuilder;
import com.negociadororq.domain.model.Factura;

/**
 * Cuerpo de notificación para el registro de una nueva factura.
 */
public class FacturaCuerpoBuilder implements CuerpoNotificacionBuilder<Factura> {

    @Override
    public Class<Factura> getTipo() {
        return Factura.class;
    }

    @Override
    public String construir(Factura factura) {
        return "Nueva factura registrada.\n\n"
                + "Número: " + factura.getNumeroFactura() + "\n"
                + "Proveedor: " + factura.getRutProveedor() + "\n"
                + "Valor: " + factura.getValorFactura() + "\n";
    }
}

