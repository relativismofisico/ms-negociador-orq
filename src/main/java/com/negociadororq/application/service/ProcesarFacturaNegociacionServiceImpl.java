package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.User;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Log
@Service
@RequiredArgsConstructor
public class ProcesarFacturaNegociacionServiceImpl implements ProcesarFacturaNegociacionService {

    private final FacturaOutputPort facturaPort;
    private final CupoConsumidorService cupoConsumidorService;
    private final UserOutputPort userPort;
    private final CompanyOutputPort companyPort;
    private final EmpresaQuotaOutputPort empresaQuotaPort;
    private final FondeadorQuotaOutputPort fondeadorQuotaPort;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final ValidacionFacturaService validacionFacturaService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada reqFact) {

        String numero = reqFact.getNumeroFactura();

        Factura factura = facturaPort.findByNumeroFactura(numero)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + numero));

        // ==========================================
        // VALIDAR VENCIMIENTO
        // ==========================================

        ValidacionFacturaResponseDto validacion =
                validacionFacturaService.validarFacturaVencida(numero, null);

        if (!validacion.isValida()) {
            throw new RuntimeException(validacion.getMensaje());
        }

        // ==========================================
        // VALIDAR ESTADO
        // ==========================================

        if (!"SIN_NEGOCIAR".equalsIgnoreCase(factura.getEstadoNegociacion())) {
            throw new RuntimeException("Factura no negociable estado=" + factura.getEstadoNegociacion());
        }

        // ==========================================
        // VALIDAR PERMISOS
        // ==========================================

        validarPermisoOperacion(request.getTipoOperacion().getTipo(), request.getRutEmpresa(), factura);

        // ==========================================
        // OBTENER VALOR
        // ==========================================

        BigDecimal valorFactura = Optional.ofNullable(factura.getValorNetoFactura()).orElse(factura.getValorFactura());

        if (valorFactura == null) {
            valorFactura = BigDecimal.ZERO;
        }

        // ==========================================
        // OBTENER USER
        // ==========================================

        Long userId = userPort
                .findByUsername(request.getRutOperador())
                .map(User::getIdeUser)
                .orElseThrow(() ->
                        new RuntimeException("Operador no existe"));

        // ==========================================
        // VALIDAR CUPO OPERADOR
        // ==========================================

        BigDecimal cupoOperador = cupoConsumidorService.obtenerCupoDisponible(userId);

        if (cupoOperador.compareTo(valorFactura) < 0) {
            throw new RuntimeException(
                    "Operador sin cupo disponible");
        }

        // ==========================================
        // VALIDAR EMPRESA
        // ==========================================

        Long companyId = companyPort
                .findByNit(request.getRutEmpresa())
                .map(Company::getIdeCompany)
                .orElseThrow(() ->
                        new RuntimeException("Empresa no encontrada"));

        EmpresaQuota empresaQuota =
                empresaQuotaPort.findByCompanyId(companyId)
                        .orElseThrow(() ->
                                new RuntimeException("EmpresaQuota no encontrada"));

        if (empresaQuota.getCurrentQuota().compareTo(valorFactura) < 0) {
            throw new RuntimeException("Empresa sin cupo");
        }

        // ==========================================
        // FONDEADORES
        // ==========================================

        List<FondeadorAsignacion> fondeadores = obtenerFondeadores(request, reqFact);

        validarFondeadores(request, factura, fondeadores, valorFactura);

        // ==========================================
        // DESCONTAR CUPOS
        // ==========================================

        cupoConsumidorService.descontarCupo(userId, valorFactura);

        empresaQuota.setCurrentQuota(empresaQuota.getCurrentQuota().subtract(valorFactura));

        empresaQuotaPort.save(empresaQuota);

        descontarCupoFondeadores(request, factura, fondeadores, valorFactura);

        // ==========================================
        // ACTUALIZAR FACTURA
        // ==========================================

        List<ActorFactura> actores =
                construirActores(factura, fondeadores);

        EstadoFirma estadoFirma = calcularEstadoFirma(request.getTipoOperacion().getTipo());

        facturaPatchPort.marcarComoNegociada(factura.getId(), estadoFirma, actores);

        // ==========================================
        // RESPUESTA
        // ==========================================

        reqFact.setFondeadores(fondeadores);
        reqFact.setRutProveedor(factura.getRutProveedor());
        reqFact.setRutPagador(factura.getRutPagador());
        reqFact.setValorFactura(factura.getValorFactura());

        return reqFact;
    }

    private List<FondeadorAsignacion> obtenerFondeadores(
            NegociacionRequestDto request,
            FacturaNegociada reqFact
    ) {

        if (reqFact.getFondeadores() != null
                && !reqFact.getFondeadores().isEmpty()) {

            return reqFact.getFondeadores();
        }

        return Optional.ofNullable(
                        request.getFondeadoresGlobales())
                .orElse(Collections.emptyList());
    }

    private void validarFondeadores(
            NegociacionRequestDto request,
            Factura factura,
            List<FondeadorAsignacion> fondeadores,
            BigDecimal valorFactura
    ) {

        for (FondeadorAsignacion fon : fondeadores) {

            Long funderId = companyPort
                    .findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow();

            Long supplierId = companyPort
                    .findByNit(factura.getRutProveedor())
                    .map(Company::getIdeCompany)
                    .orElseThrow();

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow();

            FondeadorQuota quota;

            if ("FACTORING".equalsIgnoreCase(
                    request.getTipoOperacion().getTipo())) {

                quota = fondeadorQuotaPort
                        .findByFunderIdAndSupplierId(
                                funderId,
                                supplierId)
                        .orElseThrow();

                if (quota.getSupplierCurrentQuota()
                        .compareTo(valorFactura) < 0) {

                    throw new RuntimeException(
                            "Fondeador sin cupo factoring");
                }

            } else {

                quota = fondeadorQuotaPort
                        .findByFunderIdAndPayerId(
                                funderId,
                                payerId)
                        .orElseThrow();

                if (quota.getPayerCurrentQuota()
                        .compareTo(valorFactura) < 0) {

                    throw new RuntimeException(
                            "Fondeador sin cupo confirming");
                }
            }
        }
    }

    private void descontarCupoFondeadores(
            NegociacionRequestDto request,
            Factura factura,
            List<FondeadorAsignacion> fondeadores,
            BigDecimal valorFactura
    ) {

        for (FondeadorAsignacion fon : fondeadores) {

            Long funderId = companyPort
                    .findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow();

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow();

            FondeadorQuota quota =
                    fondeadorQuotaPort
                            .findByFunderIdAndPayerId(
                                    funderId,
                                    payerId)
                            .orElseThrow();

            if ("FACTORING".equalsIgnoreCase(
                    request.getTipoOperacion().getTipo())) {

                quota.setSupplierCurrentQuota(
                        quota.getSupplierCurrentQuota()
                                .subtract(valorFactura));

            } else {

                quota.setPayerCurrentQuota(
                        quota.getPayerCurrentQuota()
                                .subtract(valorFactura));
            }

            fondeadorQuotaPort.save(quota);
        }
    }

    private List<ActorFactura> construirActores(
            Factura factura,
            List<FondeadorAsignacion> fondeadores
    ) {

        List<ActorFactura> actores =
                Optional.ofNullable(factura.getActores())
                        .orElse(new ArrayList<>())
                        .stream()
                        .filter(a ->
                                !"FONDEADOR".equalsIgnoreCase(
                                        a.getRolEmpresaFactura()))
                        .collect(Collectors.toList());

        for (FondeadorAsignacion fon : fondeadores) {

            actores.add(
                    new ActorFactura(
                            fon.getRutFondeador(),
                            "FONDEADOR"
                    )
            );
        }

        return actores;
    }

    private EstadoFirma calcularEstadoFirma(String tipoOperacion) {

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return EstadoFirma.SIN_DESCUENTO;
        }

        return EstadoFirma.SIN_CESION;
    }

    private void validarPermisoOperacion(
            String tipoOperacion,
            String rutEmpresaRequest,
            Factura factura
    ) {

        String empresa = normalizarNit(rutEmpresaRequest);

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {

            String proveedor =
                    normalizarNit(factura.getRutProveedor());

            if (!empresa.equals(proveedor)) {

                throw new RuntimeException(
                        "Solo proveedor puede hacer factoring");
            }

        } else {

            String pagador =
                    normalizarNit(factura.getRutPagador());

            if (!empresa.equals(pagador)) {

                throw new RuntimeException(
                        "Solo pagador puede hacer confirming");
            }
        }
    }

    private String normalizarNit(String nit) {

        if (nit == null) {
            return "";
        }

        return nit.replace(".", "")
                .replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase();
    }
}

