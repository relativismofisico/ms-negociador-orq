package com.negociadororq.application.service;


import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Negociacion;

public interface NotificacionService {

    //Envia notificacion de creacion de una negociacion
    void notificarCreacionNegociacion(Negociacion negociacion);

    void notificarEndoso(Endoso endoso);

    void notificarFirmaEndoso(AprobacionRequestDto aprobacion);

    void notificarLiquidacionFallida(Negociacion negociacion, String motivo);
}
