package com.negociadororq.application.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Job de negocio que marca como VENCIDA a las facturas negociadas próximas a
 * su fecha de vencimiento, revirtiendo el cupo consumido por negociaciones
 * que quedaron abandonadas sin completarse.
 */
@Log
@RequiredArgsConstructor
public class FacturaVencimientoService {

    private static final String SIN_NEGOCIAR = "SIN_NEGOCIAR";
    private static final String FACTORING = "FACTORING";
    private static final String CONFIRMING = "CONFIRMING";
    private static final long DIAS_LIMITE_VENCIMIENTO = 10;

    private final FacturaOutputPort facturaPort;
    private final NegociacionOutputPort negociacionPort;
    private final CompanyOutputPort companyPort;
    /** Fase 28d (A2): reversión exacta de lo descontado al crear la negociación. */
    private final ReversionExactaService reversionExacta;
    private final ClockPort clock;

    /**
     * Marca facturas como VENCIDA
     * cuando quedan <= 10 días.
     */
    public void actualizarFacturasVencidas() {

        log.info("Iniciando validacion nocturna de vencimientos");

        List<Factura> facturas = facturaPort.findFacturasPendientesVencimiento();

        int actualizadas = 0;

        LocalDate hoy = clock.now().toLocalDate();

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
                if (dias <= DIAS_LIMITE_VENCIMIENTO) {

                    // findFacturasPendientesVencimiento() trae cualquier estado
                    // no terminal (NEGOCIADA, EN_REVISION, EN_APROBACION, APROBADA,
                    // PARCIAL...), no solo SIN_NEGOCIAR — si ya se consumieron
                    // cupos para una negociación que nunca se completó, hay que
                    // revertirlos antes de marcar la factura como vencida, o
                    // quedan atascados para siempre (hueco preexistente, cerrado
                    // acá). Si nunca se negoció (SIN_NEGOCIAR), no hay nada que
                    // revertir.
                    if (!SIN_NEGOCIAR.equalsIgnoreCase(factura.getEstadoNegociacion())) {
                        revertirCuposPorAbandono(factura);
                    }

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

    // Fase 28d (A2): la reversión devuelve exactamente lo que la creación descontó para la factura (incluido el cupo del
    // operador, que antes no se devolvía porque el operador no se guardaba, y el indirecto), una sola vez. Sin registro
    // de lo descontado, REVISION_MANUAL ([OPERACION-PENDIENTE-ATASCADA]) y nada cambia.
    private void revertirCuposPorAbandono(Factura factura) {
        if (factura.getNegociacionId() == null || factura.getCufe() == null) {
            return;
        }
        reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO, factura.getNegociacionId(), factura.getCufe(),
                "vencimiento de la factura");
    }

}
