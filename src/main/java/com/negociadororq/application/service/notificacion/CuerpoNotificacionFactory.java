package com.negociadororq.application.service.notificacion;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CuerpoNotificacionFactory {

    private final List<CuerpoNotificacionBuilder<?>> builders;

    private Map<Class<?>, CuerpoNotificacionBuilder<?>> mapa;

    @PostConstruct
    public void init() {
        mapa = new HashMap<>();
        for (CuerpoNotificacionBuilder<?> builder : builders) {
            mapa.put(builder.getTipo(), builder);
        }
    }

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
