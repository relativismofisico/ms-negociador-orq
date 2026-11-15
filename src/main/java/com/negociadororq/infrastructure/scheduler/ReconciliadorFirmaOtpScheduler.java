package com.negociadororq.infrastructure.scheduler;

import com.negociadororq.application.service.FirmaOtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reconciliador de firmas por OTP (Fase 28c): reintenta las solicitudes que quedaron en {@code OTP_VALIDADO} (caída a
 * mitad de la firma o fallo transitorio). Agotados {@code firma.reconciliador.max-intentos}, la solicitud pasa a
 * {@code FALLO_FIRMA} con el log {@code [FIRMA-FALLIDA]}. Una sola instancia a la vez (ShedLock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliadorFirmaOtpScheduler {

    private final FirmaOtpService firmaOtpService;

    /** Cada {@code firma.reconciliador.intervalo} (1 min por defecto). */
    @Scheduled(fixedDelayString = "${firma.reconciliador.intervalo:PT1M}")
    @SchedulerLock(name = "ms-negociador-orq_reconciliadorFirmaOtp",
            lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    public void reconciliar() {
        try {
            final int reintentadas = firmaOtpService.reintentarPendientes();
            if (reintentadas > 0) {
                log.info("[FIRMA-REINTENTO] reconciliador: {} firma(s) reintentada(s).", reintentadas);
            }
        } catch (RuntimeException e) {
            log.error("[FIRMA-REINTENTO] reconciliador: ciclo fallido: {}", e.getMessage());
        }
    }
}
