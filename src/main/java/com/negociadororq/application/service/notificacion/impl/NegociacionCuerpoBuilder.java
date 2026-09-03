package com.negociadororq.application.service.notificacion.impl;

import com.negociadororq.application.service.notificacion.CuerpoNotificacionBuilder;
import com.negociadororq.domain.model.Negociacion;

/**
 * Cuerpo de notificación para la creación de una nueva negociación.
 */
public class NegociacionCuerpoBuilder implements CuerpoNotificacionBuilder<Negociacion> {

    @Override
    public Class<Negociacion> getTipo() {
        return Negociacion.class;
    }

    @Override
    public String construir(Negociacion negociacion) {
        return "Se ha creado una nueva negociación.\n\n"
                + "ID: " + negociacion.getId() + "\n"
                + "Tipo: " + negociacion.getTipoOperacion().getTipo() + "\n"
                + "Total: " + negociacion.getTotalNegociado() + "\n"
                + "Facturas: " + negociacion.getFacturas().size() + "\n\n"
                + "Por favor ingrese a la plataforma para revisar.";
    }
}

