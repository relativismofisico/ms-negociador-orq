package com.negociadororq.infrastructure.scheduler;

import com.negociadororq.application.service.ReconciliadorOperacionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fase 28d, opción A: dispara el reconciliador único de {@code operacion_pendiente}. Una sola instancia a la vez
 * (ShedLock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliadorOperacionesScheduler {

    private final ReconciliadorOperacionesService reconciliador;

    /** Cada {@code operaciones.reconciliador.intervalo} (1 min por defecto). */
    @Scheduled(fixedDelayString = "${operaciones.reconciliador.intervalo:PT1M}")
    @SchedulerLock(name = "ms-negociador-orq_reconciliadorOperaciones",
            lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    public void reconciliar() {
        try {
            final int reanudadas = reconciliador.reconciliar();
            if (reanudadas > 0) {
                log.info("[RECONCILIADOR-OPERACIONES] {} operación(es) reanudada(s).", reanudadas);
            }
        } catch (RuntimeException e) {
            log.error("[RECONCILIADOR-OPERACIONES] ciclo fallido: {}", e.getClass().getSimpleName());
        }
    }
}
