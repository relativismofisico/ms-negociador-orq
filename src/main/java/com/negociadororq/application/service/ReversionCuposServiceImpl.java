package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.Locale;

import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ReversionCuposService}.
 */
@Log
@RequiredArgsConstructor
public class ReversionCuposServiceImpl
        implements ReversionCuposService {

    private final NegociacionOutputPort negociacionPort;
    private final FacturaOutputPort facturaPort;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final EndosoPatchOutputPort endosoPatchPort;

    private final CupoReversionService cupoReversionService;
    private final UserOutputPort userPort;
    private final CompanyOutputPort companyPort;

    private final NotificacionService notificacionService;

    @Override
    @Transactional
    public void revertirPorFactura(
            String numeroFactura,
            String actorId,
            String motivo) {

        // ==========================================
        // FACTURA
        // ==========================================

        Factura factura =
                facturaPort
                        .findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Factura no encontrada"));

        // ==========================================
        // NEGOCIACIÓN
        // ==========================================

        Negociacion negociacion =
                negociacionPort
                        .findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Negociación no encontrada"));

        // ==========================================
        // FACTURA NEGOCIADA
        // ==========================================

        FacturaNegociada facturaNegociada =
                negociacion.getFacturas()
                        .stream()
                        .filter(f ->
                                f.getNumeroFactura()
                                        .equals(numeroFactura))
                        .findFirst()
                        .orElseThrow();

        // ==========================================
        // TIPO OPERACIÓN
        // ==========================================

        String tipoOperacion =
                negociacion.getTipoOperacion()
                        .getTipo()
                        .toUpperCase(Locale.ROOT);

        // ==========================================
        // VALOR
        // ==========================================

        BigDecimal valor =
                facturaNegociada.getValorFactura();

        // ==========================================
        // REVERSAR CUPO OPERADOR
        // ==========================================

        Long userId =
                userPort
                        .findByUsername(actorId)
                        .map(User::getIdeUser)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Usuario no encontrado"));

        cupoReversionService.revertirCupoUsuario(
                userId,
                valor);

        // ==========================================
        // REVERSAR CUPO EMPRESA
        // ==========================================

        Long companyId =
                companyPort
                        .findByNit(negociacion.getRutEmpresa())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Empresa no encontrada"));

        cupoReversionService.revertirCupoEmpresa(
                companyId,
                valor);

        // ==========================================
        // REVERSAR CUPOS FONDEADORES
        // ==========================================

        revertirCuposFondeadores(facturaNegociada, factura, tipoOperacion, valor);

        // ==========================================
        // ACTUALIZAR ESTADOS, LOG Y NOTIFICACIÓN
        // ==========================================

        finalizarReversion(numeroFactura, motivo, negociacion);
    }

    /**
     * Revierte el cupo de cada fondeador asignado a la factura negociada, según corresponda
     * al tipo de operación (factoring: cupo fondeador-proveedor; confirming: cupo
     * fondeador-pagador).
     *
     * @param facturaNegociada factura negociada de la que se toman los fondeadores asignados
     * @param factura          factura original (para obtener proveedor/pagador)
     * @param tipoOperacion    FACTORING o CONFIRMING
     * @param valor            monto a reversar por cada fondeador
     */
    private void revertirCuposFondeadores(
            FacturaNegociada facturaNegociada, Factura factura, String tipoOperacion, BigDecimal valor) {
        if (facturaNegociada.getFondeadores() == null) {
            return;
        }

        for (FondeadorAsignacion fon : facturaNegociada.getFondeadores()) {
            Long funderId = companyPort.findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RuntimeException("Fondeador no encontrado"));

            if ("FACTORING".equals(tipoOperacion)) {
                Long supplierId = companyPort.findByNit(factura.getRutProveedor())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
                cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valor);
            }

            if ("CONFIRMING".equals(tipoOperacion)) {
                Long payerId = companyPort.findByNit(factura.getRutPagador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));
                cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valor);
            }
        }
    }

    /**
     * Marca la firma de la factura como rechazada, deja el log de auditoría y notifica la
     * liquidación fallida — el cierre común a toda reversión de cupos, sin importar por qué
     * factura o motivo se disparó.
     *
     * @param numeroFactura factura cuya firma se marca como rechazada
     * @param motivo        motivo de la reversión, para el log y la notificación
     * @param negociacion   negociación asociada, para la notificación
     */
    private void finalizarReversion(String numeroFactura, String motivo, Negociacion negociacion) {
        facturaPatchPort.actualizarEstadoFirmaPorNoFactura(numeroFactura, EstadoFirma.RECHAZADA);

        log.info(String.format("Cupos revertidos factura=%s motivo=%s", numeroFactura, motivo));

        notificacionService.notificarLiquidacionFallida(negociacion, motivo);

        log.info(String.format("Notificacion enviada negociacion=%s", negociacion.getId()));
    }
}

