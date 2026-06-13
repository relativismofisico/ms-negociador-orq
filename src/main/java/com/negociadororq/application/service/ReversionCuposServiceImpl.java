package com.negociadororq.application.service;

import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Log
@Service
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
                        .toUpperCase();

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

        if (facturaNegociada.getFondeadores() != null) {

            for (FondeadorAsignacion fon :
                    facturaNegociada.getFondeadores()) {

                Long funderId =
                        companyPort
                                .findByNit(fon.getRutFondeador())
                                .map(Company::getIdeCompany)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Fondeador no encontrado"));

                // ==================================
                // FACTORING
                // ==================================

                if ("FACTORING".equals(tipoOperacion)) {

                    Long supplierId =
                            companyPort
                                    .findByNit(
                                            factura.getRutProveedor())
                                    .map(Company::getIdeCompany)
                                    .orElseThrow(() ->
                                            new RuntimeException(
                                                    "Proveedor no encontrado"));

                    cupoReversionService
                            .revertirCupoFondeadorFactoring(
                                    funderId,
                                    supplierId,
                                    valor);
                }

                // ==================================
                // CONFIRMING
                // ==================================

                if ("CONFIRMING".equals(tipoOperacion)) {

                    Long payerId =
                            companyPort
                                    .findByNit(
                                            factura.getRutPagador())
                                    .map(Company::getIdeCompany)
                                    .orElseThrow(() ->
                                            new RuntimeException(
                                                    "Pagador no encontrado"));

                    cupoReversionService
                            .revertirCupoFondeadorConfirming(
                                    funderId,
                                    payerId,
                                    valor);
                }
            }
        }

        // ==========================================
        // ACTUALIZAR ESTADOS
        // ==========================================

        facturaPatchPort
                .actualizarEstadoFirmaPorNoFactura(
                        numeroFactura,
                        EstadoFirma.RECHAZADA);

        // ==========================================
        // LOG
        // ==========================================

        log.info(String.format("Cupos revertidos factura=%s motivo=%s", numeroFactura, motivo));

        // ==========================================
        // NOTIFICACIÓN
        // ==========================================

        notificacionService.notificarLiquidacionFallida(
                negociacion,
                motivo);

        log.info(String.format("Notificacion enviada negociacion=%s", negociacion.getId()));
    }
}

