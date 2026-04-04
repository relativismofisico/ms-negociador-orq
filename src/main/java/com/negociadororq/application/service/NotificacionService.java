package com.negociadororq.application.service;


import com.negociadororq.domain.model.Negociacion;

public interface NotificacionService {

    //Envia notificacion de creacion de una negociacion
    void notificarCreacionNegociacion(Negociacion negociacion);
}
