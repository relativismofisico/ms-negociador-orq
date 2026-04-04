package com.negociadororq.application.service.notificacion.impl;

import com.negociadororq.application.service.notificacion.CuerpoNotificacionBuilder;
import com.negociadororq.domain.model.Factura;
import org.springframework.stereotype.Component;

@Component
public class FacturaCuerpoBuilder implements CuerpoNotificacionBuilder<Factura> {

    @Override
    public Class<Factura> getTipo() {
        return Factura.class;
    }

    @Override
    public String construir(Factura f) {
        return "Nueva factura registrada.\n\n" +
                "Número: " + f.getNumeroFactura() + "\n" +
                "Proveedor: " + f.getRutProveedor() + "\n" +
                "Valor: " + f.getValorFactura() + "\n";
    }
}
