package com.negociadororq.application.service.notificacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Despacha la construcción del cuerpo de una notificación al
 * {@link CuerpoNotificacionBuilder} registrado para el tipo del objeto
 * recibido.
 */
@Component
@RequiredArgsConstructor
public class CuerpoNotificacionFactory {

    private final List<CuerpoNotificacionBuilder<?>> builders;

    private Map<Class<?>, CuerpoNotificacionBuilder<?>> mapa;

    /**
     * Indexa los builders inyectados por el tipo de dominio que cada uno declara manejar.
     */
    @PostConstruct
    public void init() {
        mapa = new HashMap<>();
        for (CuerpoNotificacionBuilder<?> builder : builders) {
            mapa.put(builder.getTipo(), builder);
        }
    }

    /**
     * @param objeto objeto de dominio a partir del cual construir el cuerpo
     * @param <T>    tipo del objeto de dominio
     * @return el cuerpo de texto de la notificación, o un mensaje indicando
     *         que no hay builder registrado para ese tipo
     */
    @SuppressWarnings("unchecked")
    public <T> String construir(T objeto) {

        CuerpoNotificacionBuilder<T> builder =
                (CuerpoNotificacionBuilder<T>) mapa.get(objeto.getClass());

        if (builder == null) {
            return "No existe builder para el tipo: " + objeto.getClass().getSimpleName();
        }

        return builder.construir(objeto);
    }
}

