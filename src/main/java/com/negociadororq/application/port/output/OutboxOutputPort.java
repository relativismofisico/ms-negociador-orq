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
     * Fase 28d (C): como {@link #guardarEvento}, con un id determinista. Si ya existe un evento con ese id no escribe
     * nada (nunca lo sobrescribe: no se vuelve a enviar).
     *
     * @param idEvento    id del evento (p. ej. {@code liquidacion.solicitada|negociacion|cufe})
     * @param aggregateId id del agregado de negocio que originó el evento
     * @param tipoEvento  tipo de evento
     * @param evento      payload
     * @return {@code true} si lo guardó; {@code false} si ya existía
     */
    boolean guardarEventoConId(String idEvento, String aggregateId, String tipoEvento, Object evento);
}
