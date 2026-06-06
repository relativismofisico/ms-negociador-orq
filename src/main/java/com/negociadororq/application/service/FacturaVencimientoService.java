package com.negociadororq.application.service;

import com.negociadororq.domain.model.Factura;
import com.negociadororq.application.port.output.FacturaOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Log
@Service
@RequiredArgsConstructor
public class FacturaVencimientoService {

    private final FacturaOutputPort facturaPort;

    /**
     * Marca facturas como VENCIDA
     * cuando quedan <= 10 días.
     */
    public void actualizarFacturasVencidas() {

        log.info("Iniciando validacion nocturna de vencimientos");

        List<Factura> facturas = facturaPort.findFacturasPendientesVencimiento();

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

                    facturaPort.save(factura);

                    actualizadas++;

                    log.info(String.format("Factura %s marcada como VENCIDA. Dias restantes=%d", factura.getNumeroFactura(), dias));
                }

            } catch (Exception ex) {

                log.log(java.util.logging.Level.SEVERE, String.format("Error validando factura %s", factura.getNumeroFactura()), ex);
            }
        }

        log.info(String.format("Proceso finalizado. Facturas vencidas actualizadas=%d", actualizadas));
    }

}

