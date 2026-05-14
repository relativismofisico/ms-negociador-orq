package com.negociadororq.infrastructure.scheduler;

import com.negociadororq.application.service.FacturaVencimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacturaVencimientoScheduler {

    private final FacturaVencimientoService facturaVencimientoService;

    /**
     * Todos los días a la 1:00 AM
     */
    //@Scheduled(cron = "${facturas.vencimiento.cron}")
    //TEST
    @Scheduled(cron = "*/10 * * * * *")
    public void ejecutarProcesoNocturno() {

        log.info(
                "🌙 Ejecutando scheduler de vencimiento facturas"
        );

        facturaVencimientoService
                .actualizarFacturasVencidas();
    }
}
