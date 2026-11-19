package com.negociadororq.infrastructure.config;

import com.negociadororq.application.service.EventosSinCufe;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Fase 28d (C): contador {@code eventos.sin.cufe} con los eventos rechazados por no traer el CUFE o la negociación de
 * la factura (log {@code [EVENTO-SIN-CUFE]}). Cualquier valor mayor que cero pide revisar la DLT.
 */
@Component
public class MetricasEventosSinCufe implements MeterBinder {

    static final String NOMBRE = "eventos.sin.cufe";

    @Override
    public void bindTo(final MeterRegistry registry) {
        FunctionCounter.builder(NOMBRE, EventosSinCufe.class, c -> EventosSinCufe.rechazados())
                .description("Eventos rechazados por no identificar la factura por CUFE")
                .register(registry);
    }
}
