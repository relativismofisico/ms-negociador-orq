package com.negociadororq.application.service;

import com.negociadororq.application.port.output.OutboxOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada del patrón Outbox usada por los servicios de negocio para guardar
 * un evento junto con su cambio de negocio, en la misma transacción.
 */
@Service
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
}
