package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.application.controller.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.model.*;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegociacionFactoringComfirmingServiceImpl implements NegociacionFactoringComfirmingService {

    private final FacturaRepository facturaRepository;
    private final NegociacionRepository negociacionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;
    private final NotificacionService notificacionService;
    private final ProcesarFacturaNegociacionService procesarFacturaNegociacionService;


    /**
     * Crea una negociación procesando factura a factura:
     * - valida cupo operador, empresa y cada fondeador-relación
     * - si una factura no tiene cupo con algún actor, se marca como NO_NEGOCIADA (no se toca su estado en BD)
     * - las facturas que si pasan, se actualizan y se descuentan cupos correspondientes
     * - devuelve en errores la lista de facturas que no se negociaron y la razón
     */
    @Override
    @Transactional
    public NegociacionResponseDto crearNegociacion(NegociacionRequestDto request) {


        List<String> errores = new ArrayList<>();
        List<FacturaNegociada> facturasAceptadas = new ArrayList<>();
        List<String> facturasRechazadas = new ArrayList<>();

        // obtener userId del operador (lanzará excepción si no existe)
        Long userId = userRepository.findByUsername(request.getRutOperador())
                .map(User::getIdeUser)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe operador con username " + request.getRutOperador(), "OPERADOR_NO_ENCONTRADO"));

        /* ======================================================
           1. Agrupar facturas por pagador SOLO en CONFIRMING
        ====================================================== */
        Map<String, List<FacturaNegociada>> facturasPorPagador = new HashMap<>();

        for (FacturaNegociada fn : request.getFacturas()) {

            Factura factura = facturaRepository.findByNumeroFactura(fn.getNumeroFactura())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Factura no encontrada: " + fn.getNumeroFactura(), "FACTURA_NO_ENCONTRADA"));

            facturasPorPagador
                    .computeIfAbsent(factura.getRutPagador(), k -> new ArrayList<>())
                    .add(fn);
        }

        // cache para companyId por NIT
        Map<String, Long> companyIdByNit = new HashMap<>();
        java.util.function.Function<String, Long> resolveCompanyId = nit -> {
            if (nit == null) return null;
            if (companyIdByNit.containsKey(nit)) return companyIdByNit.get(nit);
            Long id = companyRepository.findByNit(nit)
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe company con NIT " + nit, "EMPRESA_NO_ENCONTRADA"));
            companyIdByNit.put(nit, id);
            return id;
        };

        // Recorremos factura a factura y procesamos (validación por factura)
        List<NegociacionResponseDto> negociacionesCreadas = new ArrayList<>();

        for (FacturaNegociada facturaReq : request.getFacturas()) {

            try {
                FacturaNegociada procesada = procesarFacturaNegociacionService.procesarFactura(request, facturaReq);
                facturasAceptadas.add(procesada);

            } catch (Exception e) {
                log.error("Error procesando factura {}", facturaReq.getNumeroFactura(), e);

                errores.add(e.getMessage());
                facturasRechazadas.add(facturaReq.getNumeroFactura() + " -> " + e.getMessage());
            }
        }


        Set<String> pagadores = facturasPorPagador.keySet();

        // Construir negociación resumen con estado según resultados
        Negociacion negociacion = new Negociacion();
        negociacion.setTipoOperacion(request.getTipoOperacion());
        negociacion.setRutEmpresa(request.getRutEmpresa());

        if (pagadores.size() == 1) {
            negociacion.setRutPagador(pagadores.iterator().next());
            negociacion.setRutPagadores(null);
        } else {
            negociacion.setRutPagador(null);
            negociacion.setRutPagadores(new ArrayList<>(pagadores));
        }

        String rutProveedor;

        if ("PROVEEDOR".equalsIgnoreCase(request.getRolEmpresa())) {
            rutProveedor = request.getRutEmpresa();
        } else {
            rutProveedor = facturasAceptadas.isEmpty()
                    ? null
                    : facturasAceptadas.get(0).getRutProveedor();
        }
        negociacion.setRutProveedor(rutProveedor);
        negociacion.setFacturas(facturasAceptadas);
        negociacion.setRutFondeadores(facturasAceptadas.stream()
                .flatMap(f -> Optional.ofNullable(f.getFondeadores()).orElse(Collections.emptyList()).stream())
                .map(FondeadorAsignacion::getRutFondeador)
                .distinct()
                .collect(Collectors.toList()));

        // total negociado = suma aceptadas
        BigDecimal totalAceptado = facturasAceptadas.stream()
                .map(f -> Optional.ofNullable(f.getValorFactura()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        negociacion.setTotalNegociado(totalAceptado);

        String estado;
        if (facturasAceptadas.isEmpty()) estado = EstadoNegociacion.RECHAZADA.name();
        else if (!facturasRechazadas.isEmpty()) estado = EstadoNegociacion.PENDIENTE.name();
        else estado =EstadoNegociacion.PENDIENTE.name();

        negociacion.setEstado(estado);
        List<String> allErrores = new ArrayList<>(errores);

        // añadir motivos concretos de facturas rechazadas
        negociacion.setErrores(allErrores);
        negociacion.setFechaCreacion(LocalDateTime.now());
        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionRepository.save(negociacion);

        notificacionService.notificarCreacionNegociacion(negociacion);

        // preparar respuesta
        NegociacionResponseDto response = new NegociacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setRutEmpresa(request.getRutEmpresa());
        response.setTipoOperacion(request.getTipoOperacion());
        response.setFacturas(facturasAceptadas);
        if (pagadores.size() == 1) {
            negociacion.setRutPagador(pagadores.iterator().next());
            negociacion.setRutPagadores(null);
        } else {
            negociacion.setRutPagador(null);
            negociacion.setRutPagadores(new ArrayList<>(pagadores));
        }


        if ("PROVEEDOR".equalsIgnoreCase(request.getRolEmpresa())) {
            rutProveedor = request.getRutEmpresa();
        } else {
            rutProveedor = facturasAceptadas.isEmpty()
                    ? null
                    : facturasAceptadas.get(0).getRutProveedor();
        }
        negociacion.setRutProveedor(rutProveedor);
        response.setFondeadoresAsignados(negociacion.getRutFondeadores().stream()
                .map(r -> FondeadorAsignacion.builder().rutFondeador(r).nombreFondeador("DESCONOCIDO").build())
                .collect(Collectors.toList()));
        response.setTotalNegociado(totalAceptado);
        response.setEstado(estado);
        response.setErrores(allErrores);

        return response;
    }

    private EstadoFirma calcularEstadoFirma(String tipoOperacion) {
        if (tipoOperacion == null) return EstadoFirma.SIN_CESION;

        return switch (tipoOperacion.toUpperCase()) {
            case "FACTORING" -> EstadoFirma.SIN_CESION;
            case "CONFIRMING" -> EstadoFirma.SIN_DESCUENTO;
            default -> EstadoFirma.SIN_CESION;
        };
    }

    private void validarPermisoOperacion(String tipoOperacion,
                                         String rutEmpresaRequest,
                                         Factura factura) {

        if (tipoOperacion == null) {
            throw new RuntimeException("Tipo de operación no informado");
        }

        String tipo = tipoOperacion.toUpperCase();

        String rutEmpresa = normalizarNit(rutEmpresaRequest);

        if ("FACTORING".equals(tipo)) {

            String rutProveedor = normalizarNit(factura.getRutProveedor());

            if (!rutEmpresa.equals(rutProveedor)) {

                throw new RuntimeException(
                        "Operación FACTORING solo puede ser realizada por el proveedor de la factura. " +
                                "Empresa solicitante: " + rutEmpresaRequest +
                                ", proveedor factura: " + factura.getRutProveedor()
                );
            }

        } else if ("CONFIRMING".equals(tipo)) {

            String rutPagador = normalizarNit(factura.getRutPagador());

            if (!rutEmpresa.equals(rutPagador)) {

                throw new RuntimeException(
                        "Operación CONFIRMING solo puede ser realizada por el pagador de la factura. " +
                                "Empresa solicitante: " + rutEmpresaRequest +
                                ", pagador factura: " + factura.getRutPagador()
                );
            }

        } else {
            throw new RuntimeException("Tipo de operación no soportado: " + tipo);
        }
    }


    private Optional<FondeadorQuota> obtenerQuotaPorOperacion(String tipoOperacion, Long funderId,
                                                              Long supplierId, Long payerId) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return fondeadorQuotaRepository
                    .findByFunderIdAndSupplierId(funderId, supplierId);
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return fondeadorQuotaRepository
                    .findByFunderIdAndPayerId(funderId, payerId);
        }
        throw new RuntimeException("Tipo operación no soportado: " + tipoOperacion);
    }

    private BigDecimal obtenerCupoDisponiblePorOperacion(String tipoOperacion, FondeadorQuota quota) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return quota.getSupplierCurrentQuota() == null ? BigDecimal.ZERO : quota.getSupplierCurrentQuota();
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return quota.getPayerCurrentQuota() == null ? BigDecimal.ZERO : quota.getPayerCurrentQuota();
        }
        return BigDecimal.ZERO;
    }

    private void descontarCupoFondeador(String tipoOperacion, FondeadorQuota quota, BigDecimal valorFactura) {
        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {

            BigDecimal actual = quota.getSupplierCurrentQuota();

            if (actual == null) {
                actual = BigDecimal.ZERO;
            }

            quota.setSupplierCurrentQuota(actual.subtract(valorFactura).max(BigDecimal.ZERO));

            return;
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {

            BigDecimal actual = quota.getPayerCurrentQuota();

            if (actual == null) {
                actual = BigDecimal.ZERO;
            }

            quota.setPayerCurrentQuota(actual.subtract(valorFactura).max(BigDecimal.ZERO));
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

