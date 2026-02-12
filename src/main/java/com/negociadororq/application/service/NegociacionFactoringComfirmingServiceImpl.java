package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.domain.model.*;
import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
    private final FacturaService facturaService;
    private final OperadorService operadorService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmpresaQuotaRepository empresaQuotaRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;
    private final AprobacionNegociacionRepository aprobacionNegociacionRepository;
    private final FacturaPatchRepository facturaPatchRepository;



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
                .orElseThrow(() -> new RuntimeException("No existe operador con username " + request.getRutOperador()));


        /* ======================================================
           1. Agrupar facturas por pagador SOLO en CONFIRMING
        ====================================================== */
        Map<String, List<FacturaNegociada>> facturasPorPagador = new HashMap<>();

        if (request.getTipoOperacion() != null
                && "CONFIRMING".equalsIgnoreCase(request.getTipoOperacion().getTipo())) {

            for (FacturaNegociada fn : request.getFacturas()) {
                Factura factura = facturaRepository.findByNumeroFactura(fn.getNumeroFactura())
                        .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + fn.getNumeroFactura()));

                facturasPorPagador
                        .computeIfAbsent(factura.getRutPagador(), k -> new ArrayList<>())
                        .add(fn);
            }

        } else {
            // FACTORING u otros → todo va en una sola negociación
            facturasPorPagador.put("UNICO", request.getFacturas());
        }

        // cache para companyId por NIT
        Map<String, Long> companyIdByNit = new HashMap<>();
        java.util.function.Function<String, Long> resolveCompanyId = nit -> {
            if (nit == null) return null;
            if (companyIdByNit.containsKey(nit)) return companyIdByNit.get(nit);
            Long id = companyRepository.findByNit(nit)
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RuntimeException("No existe company con NIT " + nit));
            companyIdByNit.put(nit, id);
            return id;
        };

        // Recorremos factura a factura y procesamos (validación por factura)
        List<NegociacionResponseDto> negociacionesCreadas = new ArrayList<>();

        String rutPagador = null;
        for (Map.Entry<String, List<FacturaNegociada>> entry : facturasPorPagador.entrySet()) {

            rutPagador = entry.getKey();
            List<FacturaNegociada> facturasDelPagador = entry.getValue();

            for (FacturaNegociada reqFact : Optional.ofNullable(facturasDelPagador)
                    .orElse(Collections.emptyList())) {

                String numero = reqFact.getNumeroFactura();

                Optional<Factura> optFactura = facturaRepository.findByNumeroFactura(numero);
                if (optFactura.isEmpty()) {
                    String msg = "Factura no encontrada: " + numero;
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }

                Factura factura = optFactura.get();

                // validar estado origen
                if (!"SIN_NEGOCIAR".equalsIgnoreCase(factura.getEstadoNegociacion())) {
                    String msg = "Factura " + numero + " no puede negociarse (estado: " + factura.getEstadoNegociacion() + ")";
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }

                // valor de la factura
                BigDecimal valorFactura = Optional.ofNullable(factura.getValorNetoFactura())
                        .orElse(factura.getValorFactura());
                if (valorFactura == null) valorFactura = BigDecimal.ZERO;

                // 1) Validar cupo operador
                BigDecimal cupoOperador = operadorService.obtenerCupoDisponible(userId);
                if (cupoOperador == null) cupoOperador = BigDecimal.ZERO;
                if (cupoOperador.compareTo(valorFactura) < 0) {
                    String msg = "Operador sin cupo suficiente para factura " + numero;
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }

                // 2) Validar cupo empresa a la que pertenece el usuario (empresaQuota)
                Long companyIdOperador = resolveCompanyId.apply(request.getRutEmpresa()); // si rutEmpresa es NIT de la company
                EmpresaQuota empresaQuota = empresaQuotaRepository.findByCompanyId(companyIdOperador)
                        .orElse(null);

                if (empresaQuota == null) {
                    String msg = "No existe cuota configurada para la empresa del operador (companyId " + companyIdOperador + ")";
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }
                if (empresaQuota.getCurrentQuota().compareTo(valorFactura) < 0) {
                    String msg = "Empresa (ID " + companyIdOperador + ") sin cupo suficiente para factura " + numero + ". Disponible: " + empresaQuota.getCurrentQuota() + ", requerido: " + valorFactura;
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }

                // 3) Determinar fondeadores a usar para esta factura (si request trae fondeadores particulares, usarlos; si no, usar globales)
                List<FondeadorAsignacion> fondeadoresUsados =
                        (reqFact.getFondeadores() != null && !reqFact.getFondeadores().isEmpty())
                                ? reqFact.getFondeadores()
                                : Optional.ofNullable(request.getFondeadoresGlobales())
                                .orElse(Collections.emptyList());

                if (fondeadoresUsados == null) fondeadoresUsados = Collections.emptyList();

                if (fondeadoresUsados.isEmpty()) {
                    // si no hay fondeadores, aceptamos solo si la lógica de negocio lo permite.
                    // Aquí suponemos que al menos debe haber un fondeador; si no, rechazamos.
                    String msg = "Factura " + numero + " no tiene fondeador asignado";
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    continue;
                }

                // 4) Para cada fondeador validar su cupo con esta relación (funder_id,supplier_id,payer_id)
                boolean fondeadorOK = true;
                List<String> motivosFondeador = new ArrayList<>();

                for (FondeadorAsignacion fon : fondeadoresUsados) {
                    // obtener IDs de company para funder, supplier y payer (supplier/payer provienen de la factura)
                    Long funderCompanyId = resolveCompanyId.apply(fon.getRutFondeador());
                    Long supplierCompanyId = resolveCompanyId.apply(factura.getRutProveedor());
                    Long payerCompanyId = resolveCompanyId.apply(factura.getRutPagador());

                    // buscar fila en funder (fondeador_quota)
                    Optional<FondeadorQuota> fqOpt =
                            fondeadorQuotaRepository.findByFunderIdAndPayerId(
                                    funderCompanyId, payerCompanyId);

                    if (fqOpt.isEmpty()) {
                        fondeadorOK = false;
                        motivosFondeador.add("No existe quota fondeador para funderId=" + funderCompanyId +  ", payerId=" + payerCompanyId);
                        break;
                    }

                    FondeadorQuota fq = fqOpt.get();

                    // decidir qué current_quota revisar según operación
                    String tipoOp = Optional.ofNullable(request.getTipoOperacion()).map(Operacion::getTipo).orElse("FACTORING").toUpperCase();
                    if ("FACTORING".equals(tipoOp)) {
                        // revisar supplier_current_quota
                        BigDecimal supplierCurrent = fq.getPayerCurrentQuota() == null ? BigDecimal.ZERO : fq.getPayerCurrentQuota();
                        if (supplierCurrent.compareTo(valorFactura) < 0) {
                            fondeadorOK = false;
                            motivosFondeador.add("Fondeador " + fon.getRutFondeador() + " no tiene supplier_current_quota suficiente para factura " + numero + ". Disponible: " + supplierCurrent + ", requerido: " + valorFactura);
                            break;
                        }
                    } else if ("COMFIRMING".equals(tipoOp)) {
                        // revisar payer_current_quota
                        BigDecimal payerCurrent = fq.getPayerCurrentQuota() == null ? BigDecimal.ZERO : fq.getPayerCurrentQuota();
                        if (payerCurrent.compareTo(valorFactura) < 0) {
                            fondeadorOK = false;
                            motivosFondeador.add("Fondeador " + fon.getRutFondeador() + " no tiene payer_current_quota suficiente para factura " + numero + ". Disponible: " + payerCurrent + ", requerido: " + valorFactura);
                            break;
                        }
                    } else {
                        // default: revisar supplier_current_quota
                        BigDecimal supplierCurrent = fq.getPayerCurrentQuota()== null ? BigDecimal.ZERO : fq.getPayerCurrentQuota();
                        if (supplierCurrent.compareTo(valorFactura) < 0) {
                            fondeadorOK = false;
                            motivosFondeador.add("Fondeador " + fon.getRutFondeador() + " no tiene supplier_current_quota suficiente (operación desconocida).");
                            break;
                        }
                    }
                } // end for fondeadores

                if (!fondeadorOK) {
                    String razon = String.join("; ", motivosFondeador);
                    errores.add("Factura " + numero + " rechazada por fondeador: " + razon);
                    facturasRechazadas.add(numero + " -> " + razon);
                    continue;
                }

                // === Si llegamos hasta aquí: TODOS los checks pasaron para esta factura ===
                // Procedemos a aplicar los cambios (descontar cupos y actualizar factura)
                try {
                    // Descontar cupo operador (cada factura)
                    operadorService.descontarCupo(userId, valorFactura);

                    // Descontar cupo empresa
                    BigDecimal nuevoEmpresaCupo = empresaQuota.getCurrentQuota().subtract(valorFactura).max(BigDecimal.ZERO);
                    empresaQuota.setCurrentQuota(nuevoEmpresaCupo);
                    empresaQuotaRepository.save(empresaQuota);

                    // Descontar cupo de cada fondeador en su relación
                    String tipoOp = Optional.ofNullable(request.getTipoOperacion()).map(Operacion::getTipo).orElse("FACTORING").toUpperCase();
                    for (FondeadorAsignacion fon : fondeadoresUsados) {
                        Long funderCompanyId = resolveCompanyId.apply(fon.getRutFondeador());
                        Long supplierCompanyId = resolveCompanyId.apply(factura.getRutProveedor());
                        Long payerCompanyId = resolveCompanyId.apply(factura.getRutPagador());

                        FondeadorQuota fq = fondeadorQuotaRepository.findByFunderIdAndPayerId(
                                        funderCompanyId, payerCompanyId)
                                .orElseThrow(() -> new RuntimeException("Quota fondeador desapareció durante la operación"));

                        if ("FACTORING".equals(tipoOp)) {
                            BigDecimal nuevoSupplier = fq.getPayerCurrentQuota().subtract(valorFactura).max(BigDecimal.ZERO);
                            fq.setPayerCurrentQuota(nuevoSupplier);
                        } else if ("CONFIRMING".equals(tipoOp)) {
                            BigDecimal nuevoPayer = fq.getPayerCurrentQuota().subtract(valorFactura).max(BigDecimal.ZERO);
                            fq.setPayerCurrentQuota(nuevoPayer);
                        } else {
                            BigDecimal nuevoSupplier = fq.getPayerCurrentQuota().subtract(valorFactura).max(BigDecimal.ZERO);
                            fq.setPayerCurrentQuota(nuevoSupplier);
                        }
                        fondeadorQuotaRepository.save(fq);
                    }

                    // Actualizar factura: estados y actores
                    List<FondeadorAsignacion> finalFondeadores = fondeadoresUsados;
                    List<ActorFactura> actoresActualizados = Optional.ofNullable(factura.getActores())
                            .orElse(new ArrayList<>())
                            .stream()
                            .filter(a -> !"FONDEADOR".equalsIgnoreCase(a.getRolEmpresaFactura()))
                            .collect(Collectors.toList());
                    for (FondeadorAsignacion fon : finalFondeadores) {
                        actoresActualizados.add(new ActorFactura(fon.getRutFondeador(), "FONDEADOR"));
                    }

                    EstadoFirma estadoFirma = calcularEstadoFirma(request.getTipoOperacion().getTipo());

                    facturaPatchRepository.marcarComoNegociada(factura.getId(), estadoFirma, actoresActualizados);
                    // construir DTO aceptado
                    reqFact.setFondeadores(finalFondeadores);
                    reqFact.setRutProveedor(factura.getRutProveedor());
                    reqFact.setRutPagador(factura.getRutPagador());
                    reqFact.setValorFactura(factura.getValorFactura());
                    facturasAceptadas.add(reqFact);

                } catch (Exception e) {
                    // si falla la aplicación de cambios en esta factura, añadir al rechazo con motivo
                    String msg = "Error aplicando cambios a factura " + numero + ": " + e.getMessage();
                    log.error(msg, e);
                    errores.add(msg);
                    facturasRechazadas.add(numero + " -> " + msg);
                    // NOTA: aquí no revertimos las facturas previamente aplicadas (porque queremos parcial),
                    // pero podríamos implementar compensaciones si lo deseas.
                }
            } // end for each factura


        }

        // Construir negociación resumen con estado según resultados
        Negociacion negociacion = new Negociacion();
        negociacion.setTipoOperacion(request.getTipoOperacion());
        negociacion.setRutEmpresa(request.getRutEmpresa());
        negociacion.setRutPagador("UNICO".equals(rutPagador) ? null : rutPagador);
        //negociacion.setRutProveedor("PROVEEDOR".equalsIgnoreCase(request.getRolEmpresa()) ? request.getRutEmpresa() : null);
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

        // preparar respuesta
        NegociacionResponseDto response = new NegociacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setRutEmpresa(request.getRutEmpresa());
        response.setTipoOperacion(request.getTipoOperacion());
        response.setFacturas(facturasAceptadas);
        negociacion.setRutPagador("UNICO".equals(rutPagador) ? null : rutPagador);
        response.setFondeadoresAsignados(negociacion.getRutFondeadores().stream()
                .map(r -> FondeadorAsignacion.builder().rutFondeador(r).nombreFondeador("DESCONOCIDO").build())
                .collect(Collectors.toList()));
        response.setTotalNegociado(totalAceptado);
        response.setEstado(estado);
        response.setErrores(allErrores);

        return response;
    }

    private String determinarRolActor(String rutEmpresa, Negociacion negociacion) {
        // Cache para evitar múltiples llamadas a Mongo
        Map<String, Boolean> cache = new HashMap<>();

        List<String> numerosFacturas = negociacion.getFacturas().stream()
                .map(FacturaNegociada::getNumeroFactura)
                .collect(Collectors.toList());

        // 1️⃣ Revisar si el RUT es Pagador
        cache.computeIfAbsent("pagador", k ->
                facturaRepository.existsByNumeroFacturaInAndRutPagador(numerosFacturas, rutEmpresa)
        );
        if (cache.get("pagador")) return "PAGADOR";

        // 2️⃣ Revisar si el RUT es Proveedor
        cache.computeIfAbsent("proveedor", k ->
                facturaRepository.existsByNumeroFacturaInAndRutProveedor(numerosFacturas, rutEmpresa)
        );
        if (cache.get("proveedor")) return "PROVEEDOR";

        // 3️⃣ Revisar si el RUT es Fondeador (nivel factura)
        boolean esFondeador = negociacion.getFacturas().stream()
                .filter(f -> f.getFondeadores() != null)
                .flatMap(f -> f.getFondeadores().stream())
                .anyMatch(f -> rutEmpresa.equals(f.getRutFondeador()));
        if (esFondeador) return "FONDEADOR";

        // 4️⃣ Si no coincide con ninguno
        return "DESCONOCIDO";
    }



    private EstadoFirma calcularEstadoFirma(String tipoOperacion) {
        if (tipoOperacion == null) return EstadoFirma.SIN_CESION;

        return switch (tipoOperacion.toUpperCase()) {
            case "FACTORING" -> EstadoFirma.SIN_CESION;
            case "CONFIRMING" -> EstadoFirma.SIN_DESCUENTO;
            default -> EstadoFirma.SIN_CESION;
        };
    }
}
