package com.negociadororq.infrastructure.alerta;

import java.util.concurrent.atomic.AtomicLong;

import com.negociadororq.application.port.output.AlertaFirmaOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Alertas de la firma por OTP (Fase 28c) por log, cada una con su total acumulado desde el arranque:
 * {@code [ALERTA][FIRMA-FALLIDA]} por cada firma fallida y {@code [ENDOSO-DUPLICADO]} por cada endoso duplicado. Las
 * alarmas se arman con filtros de métrica de CloudWatch sobre esas etiquetas. El servicio no expone métricas de
 * Micrometer.
 */
@Slf4j
@Component
public class AlertaFirmaLogAdapter implements AlertaFirmaOutputPort {

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong endososDuplicados = new AtomicLong();
    private final AtomicLong rolesNoPermitidos = new AtomicLong();

    @Override
    public void firmaFallida() {
        log.error("[ALERTA][FIRMA-FALLIDA] firmas fallidas desde el arranque: {}", total.incrementAndGet());
    }

    @Override
    public void endosoDuplicado(final String idNegociacion) {
        log.warn("[ENDOSO-DUPLICADO] negociación {}: el actor ya había firmado esta aprobación con otro OTP; se descarta la "
                + "solicitud. Endosos duplicados desde el arranque: {}", idNegociacion, endososDuplicados.incrementAndGet());
    }

    @Override
    public void rolNoPermitido(final String idNegociacion, final String rutEmpresa, final String tipoOperacion,
            final String rol) {
        log.warn("[ROL-NO-PERMITIDO] negociación {} empresa {}: el rol {} no aprueba operaciones de {}; la solicitud queda "
                + "en ROL_NO_PERMITIDO sin firma ni cupos. Desde el arranque: {}", idNegociacion, rutEmpresa, rol,
                tipoOperacion, rolesNoPermitidos.incrementAndGet());
    }

    /**
     * @return firmas fallidas desde el arranque
     */
    long total() {
        return total.get();
    }

    /**
     * @return endosos duplicados desde el arranque
     */
    long endososDuplicados() {
        return endososDuplicados.get();
    }

    /**
     * @return validaciones de un rol no permitido desde el arranque
     */
    long rolesNoPermitidos() {
        return rolesNoPermitidos.get();
    }
}
