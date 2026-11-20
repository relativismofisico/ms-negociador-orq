package com.negociadororq.application.port.output;

import com.negociadororq.domain.event.NotificacionEmailEvent;

/**
 * Fase 28d (A2): correo registrado en el outbox JDBC de ms-commons-util ({@code outbox_negociador}) dentro de la
 * transacción MySQL activa, con id de evento propio: se confirma o se revierte junto con el cambio, y un id repetido no
 * registra otro (ni se pierde ni se duplica).
 */
public interface CorreoTransaccionalOutputPort {

    /**
     * @param eventId id determinista del evento (máximo 64 caracteres)
     * @param evento  correo
     * @return true si se registró; false si ese id ya estaba
     */
    boolean registrar(String eventId, NotificacionEmailEvent evento);
}
