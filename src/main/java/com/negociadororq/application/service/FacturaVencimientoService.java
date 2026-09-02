package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.Negociacion;
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
    private final CupoReversionService cupoReversionService;
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

    // rutOperador no se persiste ni en Factura ni en Negociacion: no hay forma
    // de identificar qué operador consumió el cupo para revertirle el suyo acá
    // (limitación conocida, distinta a la reversión de EndosoDescuentoServiceImpl,
    // que sí tiene el rutOperador de la request). Se revierten empresa y
    // fondeadores, que sí son identificables desde la Negociacion.
    private void revertirCuposPorAbandono(Factura factura) {
        if (factura.getNegociacionId() == null) {
            return;
        }

        Optional<Negociacion> negociacionOpt = negociacionPort.findById(factura.getNegociacionId());
        if (negociacionOpt.isEmpty()) {
            return;
        }
        Negociacion negociacion = negociacionOpt.get();

        BigDecimal valor = Optional.ofNullable(factura.getValorNetoFactura())
                .orElse(Optional.ofNullable(factura.getValorFactura()).orElse(BigDecimal.ZERO));

        companyPort.findByNit(negociacion.getRutEmpresa()).map(Company::getIdeCompany)
                .ifPresent(companyId -> cupoReversionService.revertirCupoEmpresa(companyId, valor));

        String tipoOperacion = null;
        if (negociacion.getTipoOperacion() != null) {
            tipoOperacion = negociacion.getTipoOperacion().getTipo();
        }
        List<String> rutFondeadores = Optional.ofNullable(negociacion.getRutFondeadores())
                .orElse(Collections.emptyList());

        for (String rutFondeador : rutFondeadores) {
            Long funderId = companyPort.findByNit(rutFondeador).map(Company::getIdeCompany).orElse(null);
            if (funderId == null) {
                continue;
            }

            if (FACTORING.equalsIgnoreCase(tipoOperacion)) {
                companyPort.findByNit(factura.getRutProveedor()).map(Company::getIdeCompany)
                        .ifPresent(supplierId ->
                                cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valor));
            } else if (CONFIRMING.equalsIgnoreCase(tipoOperacion)) {
                companyPort.findByNit(factura.getRutPagador()).map(Company::getIdeCompany)
                        .ifPresent(payerId ->
                                cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valor));
            }
        }
    }

}
