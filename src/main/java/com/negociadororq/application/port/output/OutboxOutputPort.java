package com.negociadororq.application.port.output;

/**
 * Puerto del patrón Outbox: persiste eventos junto con el cambio de negocio
 * para que un job aparte los publique de forma confiable.
 */
public interface OutboxOutputPort {

    /**
     * @param aggregateId id del agregado de negocio que originó el evento
     * @param tipoEvento  tipo de evento, usado por el consumidor para deserializar
     * @param evento      payload del evento a serializar y guardar
     */
    void guardarEvento(String aggregateId, String tipoEvento, Object evento);
}
