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

    /**
     * Fase 28d: guarda el evento con un id determinista. Si ya existe un evento con ese id (un intento anterior lo
     * escribió y murió antes de marcarlo como emitido), no escribe otro.
     *
     * @param eventId     id determinista del evento
     * @param aggregateId id del agregado de negocio que originó el evento
     * @param tipoEvento  tipo de evento, usado por el consumidor para deserializar
     * @param evento      payload del evento a serializar y guardar
     * @return {@code true} si lo escribió; {@code false} si ya existía
     */
    boolean guardarEventoUnico(String eventId, String aggregateId, String tipoEvento, Object evento);
}
