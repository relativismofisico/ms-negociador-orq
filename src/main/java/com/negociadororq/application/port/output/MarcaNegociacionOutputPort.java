package com.negociadororq.application.port.output;

/**
 * Fase 28d (A2): marcador único por negociación ({@code negociacion_marca}, {@code _id} = id de la negociación). La
 * creación inserta CREADA en su transacción Mongo; el reconciliador inserta CANCELADA antes de revertir. Solo uno de
 * los dos entra: el otro choca por clave duplicada.
 */
public interface MarcaNegociacionOutputPort {

    /** Tipo del marcador. */
    enum Tipo {
        /** La creación terminó su paso Mongo. */
        CREADA,
        /** El reconciliador canceló la creación (y revierte los cupos). */
        CANCELADA
    }

    /**
     * @param negociacionId id de la negociación
     * @param tipo          CREADA o CANCELADA
     * @return true si entró; false si ya había un marcador con ese id
     */
    boolean crear(String negociacionId, Tipo tipo);

    /**
     * @param negociacionId id de la negociación
     * @return el marcador, si hay
     */
    java.util.Optional<Tipo> buscar(String negociacionId);
}
