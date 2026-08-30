package com.negociadororq.infrastructure.scheduler;

import com.negociadororq.application.service.FacturaVencimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara periódicamente el proceso nocturno que marca facturas próximas a vencer como VENCIDA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacturaVencimientoScheduler {

    private final FacturaVencimientoService facturaVencimientoService;

    @Scheduled(cron = "${facturas.vencimiento.cron}")
    @SchedulerLock(name = "ms-negociador-orq_facturaVencimiento_ejecutarProcesoNocturno", lockAtMostFor = "PT30M")
    public void ejecutarProcesoNocturno() {

        log.info("Ejecutando scheduler de vencimiento facturas");

        facturaVencimientoService
                .actualizarFacturasVencidas();
    }
}

