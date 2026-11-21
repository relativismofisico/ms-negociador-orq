package com.negociadororq.infrastructure.scheduler;

import com.negociadororq.application.service.SeguimientoLiquidacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fase 28d (A4): dispara el seguimiento de las solicitudes de liquidación sin resultado. Una sola instancia a la vez
 * (ShedLock). El intervalo es también la espera entre reenvíos e intentos de cancelación.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeguimientoLiquidacionScheduler {

    private final SeguimientoLiquidacionService seguimiento;

    /** Cada {@code liquidacion.seguimiento.intervalo} (15 min por defecto). */
    @Scheduled(fixedDelayString = "${liquidacion.seguimiento.intervalo:PT15M}")
    @SchedulerLock(name = "ms-negociador-orq_seguimientoLiquidacion", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    public void seguir() {
        try {
            final int atendidas = seguimiento.seguir();
            if (atendidas > 0) {
                log.info("[LIQUIDACION-SEGUIMIENTO] {} solicitud(es) atendida(s).", atendidas);
            }
        } catch (RuntimeException e) {
            log.error("[LIQUIDACION-SEGUIMIENTO] ciclo fallido: {}", e.getClass().getSimpleName());
        }
    }
}
