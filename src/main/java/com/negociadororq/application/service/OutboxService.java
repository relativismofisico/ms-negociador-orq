package com.negociadororq.application.service;

import com.negociadororq.application.port.output.OutboxOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada del patrón Outbox usada por los servicios de negocio para guardar
 * un evento junto con su cambio de negocio, en la misma transacción.
 */
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxOutputPort outboxPort;

    /**
     * @param aggregateId id del agregado de negocio que originó el evento
     * @param tipoEvento  tipo de evento, usado por el consumidor para deserializar
     * @param evento      payload del evento a serializar y guardar
     */
    @Transactional
    public void guardarEvento(String aggregateId, String tipoEvento, Object evento) {
        outboxPort.guardarEvento(aggregateId, tipoEvento, evento);
    }

    /**
     * Fase 28d (A4): vuelve a dejar pendiente de envío un evento ya escrito.
     *
     * @param idEvento id del evento
     * @return true si existía
     */
    public boolean reencolar(String idEvento) {
        return outboxPort.reencolar(idEvento);
    }

    /**
     * Fase 28d (C): con id determinista; si ya existe, no escribe nada.
     *
     * @param idEvento    id del evento
     * @param aggregateId id del agregado de negocio que originó el evento
     * @param tipoEvento  tipo de evento
     * @param evento      payload
     * @return {@code true} si lo guardó; {@code false} si ya existía
     */
    @Transactional
    public boolean guardarEventoConId(String idEvento, String aggregateId, String tipoEvento, Object evento) {
        return outboxPort.guardarEventoConId(idEvento, aggregateId, tipoEvento, evento);
    }
}
