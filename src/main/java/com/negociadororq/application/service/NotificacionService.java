package com.negociadororq.application.service;

import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Negociacion;

public interface NotificacionService {
    void notificarCreacionNegociacion(Negociacion negociacion);
    void notificarEndoso(Endoso endoso);
    void notificarFirmaEndoso(AprobacionRequest aprobacion);
    void notificarLiquidacionFallida(Negociacion negociacion, String motivo);
}
