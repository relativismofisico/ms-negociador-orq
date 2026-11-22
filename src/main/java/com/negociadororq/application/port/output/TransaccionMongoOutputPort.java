package com.negociadororq.application.port.output;

import java.util.function.Supplier;

/**
 * Fase 28d (A2): una transacción Mongo (replica set). Todo lo que escriben los adaptadores Mongo dentro de
 * {@link #ejecutar} se confirma o se revierte junto. Es independiente de la transacción JPA (MySQL): el gestor de
 * transacciones Mongo no es un bean, así que {@code @Transactional} sigue siendo solo JPA y nunca queda ambiguo.
 */
public interface TransaccionMongoOutputPort {

    /**
     * @param accion lo que se escribe en Mongo
     * @param <T>    resultado
     * @return el resultado de la acción, ya confirmado
     */
    <T> T ejecutar(Supplier<T> accion);

    /**
     * Fase 28d (A5): como {@link #ejecutar}, pero si la transacción aborta por un conflicto transitorio (otra escritura
     * sobre el mismo documento, p. ej. el job de vencimiento) se reintenta entera, hasta 5 veces. La acción debe ser
     * solo escrituras Mongo de esa transacción.
     *
     * @param accion lo que se escribe en Mongo
     * @param <T>    resultado
     * @return el resultado de la acción, ya confirmado
     */
    <T> T ejecutarConReintento(Supplier<T> accion);
}
