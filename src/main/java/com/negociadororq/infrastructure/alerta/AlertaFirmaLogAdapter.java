package com.negociadororq.infrastructure.alerta;

import java.util.concurrent.atomic.AtomicLong;

import com.negociadororq.application.port.output.AlertaFirmaOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Alerta de firmas fallidas (Fase 28c) por log: cada una deja {@code [ALERTA][FIRMA-FALLIDA]} con el total acumulado
 * desde el arranque. La alarma se arma con un filtro de métrica de CloudWatch sobre esa etiqueta (cualquier
 * ocurrencia). El servicio no expone métricas de Micrometer.
 */
@Slf4j
@Component
public class AlertaFirmaLogAdapter implements AlertaFirmaOutputPort {

    private final AtomicLong total = new AtomicLong();

    @Override
    public void firmaFallida() {
        log.error("[ALERTA][FIRMA-FALLIDA] firmas fallidas desde el arranque: {}", total.incrementAndGet());
    }

    /**
     * @return firmas fallidas desde el arranque
     */
    long total() {
        return total.get();
    }
}
