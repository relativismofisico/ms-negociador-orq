package com.negociadororq.infrastructure.config;

import com.negociadororq.application.service.ReconciliadorOperacionesService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Fase 28d, opción A: contador {@code operaciones.pendientes.atascadas} con las operaciones que el reconciliador pasó a
 * {@code REVISION_MANUAL} desde el arranque (log {@code [OPERACION-PENDIENTE-ATASCADA]}). Cualquier valor mayor que
 * cero pide revisar la tabla {@code operacion_pendiente}.
 */
@Component
public class MetricasOperacionesPendientes implements MeterBinder {

    static final String NOMBRE = "operaciones.pendientes.atascadas";

    @Override
    public void bindTo(final MeterRegistry registry) {
        FunctionCounter.builder(NOMBRE, ReconciliadorOperacionesService.class,
                        c -> ReconciliadorOperacionesService.atascadas())
                .description("Operaciones pendientes pasadas a REVISION_MANUAL por el reconciliador")
                .register(registry);
    }
}
