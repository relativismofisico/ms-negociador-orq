package com.negociadororq.application.service.notificacion;

public interface CuerpoNotificacionBuilder<T> {

    Class<T> getTipo();

    String construir(T objeto);
}
