package com.negociadororq.application.service;

import com.negociadororq.domain.model.Factura;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaVencimientoService {

    private final FacturaRepository facturaRepository;

    /**
     * Marca facturas como VENCIDA
     * cuando quedan <= 10 días.
     */
    public void actualizarFacturasVencidas() {

        log.info("⏳ Iniciando validación nocturna de vencimientos");

        List<Factura> facturas = facturaRepository.findFacturasPendientesVencimiento();

        int actualizadas = 0;

        LocalDate hoy = LocalDate.now();

        for (Factura factura : facturas) {

            try {

                if (factura.getFechavencimientoFactura() == null) {
                    continue;
                }

                LocalDate fechaVencimiento =
                        factura.getFechavencimientoFactura();

                long dias =
                        ChronoUnit.DAYS.between(
                                hoy,
                                fechaVencimiento
                        );

                // 🔥 REGLA NEGOCIO
                if (dias <= 10) {

                    factura.setEstadoNegociacion("VENCIDA");

                    facturaRepository.save(factura);

                    actualizadas++;

                    log.info(
                            "⚠ Factura {} marcada como VENCIDA. Dias restantes={}",
                            factura.getNumeroFactura(),
                            dias
                    );
                }

            } catch (Exception ex) {

                log.error(
                        "Error validando factura {}",
                        factura.getNumeroFactura(),
                        ex
                );
            }
        }

        log.info(
                "✅ Proceso finalizado. Facturas vencidas actualizadas={}",
                actualizadas
        );
    }

}
