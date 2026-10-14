package com.negociadororq.application.port.output;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;

/**
 * Puerto de salida hacia el log de auditoría de la plataforma (topic {@code auditoria.accion}, ms-auditoria-util).
 *
 * <p>La implementación guarda el evento en el outbox, en la misma transacción que el cambio de negocio: si la
 * transacción se revierte no queda una acción auditada que no ocurrió, y si Kafka falla el outbox lo reintenta.</p>
 */
public interface AuditoriaOutputPort {

    /**
     * @param evento la acción, armada con {@code AuditoriaAccionEvent.builder()}
     */
    void registrar(AuditoriaAccionEvent evento);
}
