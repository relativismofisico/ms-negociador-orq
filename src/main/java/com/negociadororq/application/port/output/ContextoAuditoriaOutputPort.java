package com.negociadororq.application.port.output;

import com.factoring.commons.auditoria.AuditoriaActor;

/**
 * Quién, desde dónde y en qué request, para la request HTTP en curso. El actor sale de la autenticación ya validada
 * (JWT), nunca del cuerpo.
 */
public interface ContextoAuditoriaOutputPort {

    /**
     * @return el actor de la request en curso; ANONIMO si no hay request
     */
    AuditoriaActor actorActual();

    /**
     * @return la IP del cliente resuelta en el servidor, o {@code null} si no hay request
     */
    String ipActual();

    /**
     * @return el requestId (MDC) de la request en curso, o {@code null}
     */
    String requestIdActual();
}
