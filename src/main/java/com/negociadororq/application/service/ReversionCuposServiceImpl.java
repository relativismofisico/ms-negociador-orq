package com.negociadororq.application.service;

import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import com.negociadororq.enums.EstadoEndoso;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.infrastructure.persistence.repository.CompanyRepository;
import com.negociadororq.infrastructure.persistence.repository.EndosoPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReversionCuposServiceImpl
        implements ReversionCuposService {

    private final NegociacionRepository negociacionRepository;
    private final FacturaRepository facturaRepository;
    private final FacturaPatchRepository facturaPatchRepository;
    private final EndosoPatchRepository endosoPatchRepository;

    private final CupoReversionService cupoReversionService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

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
                facturaRepository
                        .findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Factura no encontrada"));

        // ==========================================
        // NEGOCIACIÓN
        // ==========================================

        Negociacion negociacion =
                negociacionRepository
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
                userRepository
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
                companyRepository
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
                        companyRepository
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
                            companyRepository
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
                            companyRepository
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

        facturaPatchRepository
                .actualizarEstadoFirmaPorNoFactura(
                        numeroFactura,
                        EstadoFirma.RECHAZADA);

        endosoPatchRepository
                .actualizarEstadoEndosoPorNoFactura(
                        negociacion.getId(),
                        numeroFactura,
                        EstadoEndoso.RECHAZADO);

        // ==========================================
        // LOG
        // ==========================================

        log.info(
                "Cupos revertidos factura={} motivo={}",
                numeroFactura,
                motivo);

        // ==========================================
        // NOTIFICACIÓN
        // ==========================================

        notificacionService.notificarLiquidacionFallida(
                negociacion,
                motivo);

        log.info(
                "Notificación enviada negociación={}",
                negociacion.getId());
    }
}
