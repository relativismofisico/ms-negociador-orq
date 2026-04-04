package com.negociadororq.application.service.notificacion.impl;

import com.negociadororq.application.service.notificacion.CuerpoNotificacionBuilder;
import com.negociadororq.domain.model.Negociacion;
import org.springframework.stereotype.Component;

@Component
public class NegociacionCuerpoBuilder implements CuerpoNotificacionBuilder<Negociacion> {

    @Override
    public Class<Negociacion> getTipo() {
        return Negociacion.class;
    }

    @Override
    public String construir(Negociacion n) {
        return "Se ha creado una nueva negociación.\n\n" +
                "ID: " + n.getId() + "\n" +
                "Tipo: " + n.getTipoOperacion().getTipo() + "\n" +
                "Total: " + n.getTotalNegociado() + "\n" +
                "Facturas: " + n.getFacturas().size() + "\n\n" +
                "Por favor ingrese a la plataforma para revisar.";
    }
}
