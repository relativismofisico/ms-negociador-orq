package com.negociadororq.application.service;

import java.util.function.Supplier;

import com.negociadororq.application.port.output.TransaccionMongoOutputPort;

/** Pruebas: ejecuta la acción sin transacción (los puertos son dobles). */
public final class TransaccionMongoDirecta implements TransaccionMongoOutputPort {

    /** Instancia única. */
    public static final TransaccionMongoDirecta INSTANCIA = new TransaccionMongoDirecta();

    private TransaccionMongoDirecta() {
    }

    @Override
    public <T> T ejecutar(final Supplier<T> accion) {
        return accion.get();
    }

    @Override
    public <T> T ejecutarConReintento(final Supplier<T> accion) {
        return accion.get();
    }
}
