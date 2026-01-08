package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.domain.model.*;
import com.negociadororq.infrastructure.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegociacionServiceImpl implements NegociacionService {

    private final FacturaRepository facturaRepository;
    private final NegociacionRepository negociacionRepository;
    private final FacturaService facturaService;
    private final OperadorService operadorService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmpresaQuotaRepository empresaQuotaRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;
    private final AprobacionNegociacionRepository aprobacionNegociacionRepository;
    private final MongoTemplate mongoTemplate;


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
        for (FacturaNegociada reqFact : Optional.ofNullable(request.getFacturas()).orElse(Collections.emptyList())) {
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
            BigDecimal valorFactura = Optional.ofNullable(factura.getValorNetoFactura()).orElse(factura.getValorFactura());
            if (valorFactura == null) valorFactura = BigDecimal.ZERO;

            // 1) Validar cupo del operador (se consulta en cada factura para reflejar decrementos previos)
            BigDecimal cupoOperador = operadorService.obtenerCupoDisponible(userId);
            if (cupoOperador == null) cupoOperador = BigDecimal.ZERO;
            if (cupoOperador.compareTo(valorFactura) < 0) {
                String msg = "Operador sin cupo suficiente para factura " + numero + ". Disponible: " + cupoOperador + ", requerido: " + valorFactura;
                errores.add(msg);
                facturasRechazadas.add(numero + " -> " + msg);
                continue; // no procesar esta factura
            }

            // 2) Validar cupo de la empresa a la que pertenece el usuario (empresaQuota)
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
            List<FondeadorAsignacion> fondeadoresUsados = (reqFact.getFondeadores() != null && !reqFact.getFondeadores().isEmpty())
                    ? reqFact.getFondeadores()
                    : Optional.ofNullable(request.getFondeadoresGlobales()).orElse(Collections.emptyList());

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
                Long funderCompanyId;
                Long supplierCompanyId;
                Long payerCompanyId;
                try {
                    funderCompanyId = resolveCompanyId.apply(fon.getRutFondeador());
                    supplierCompanyId = resolveCompanyId.apply(factura.getRutProveedor());
                    payerCompanyId = resolveCompanyId.apply(factura.getRutPagador());
                } catch (RuntimeException e) {
                    fondeadorOK = false;
                    motivosFondeador.add("No se resolvió company para fondeador/proveedor/pagador: " + e.getMessage());
                    break;
                }

                // buscar fila en funder (fondeador_quota)
                Optional<FondeadorQuota> fqOpt = fondeadorQuotaRepository.findByFunderIdAndPayerId(
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
                } else if ("CONFIRMING".equals(tipoOp)) {
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

                //
                Query query = Query.query(
                        Criteria.where("_id").is(factura.getId())
                );

                Update update = new Update()
                        .set("estadoNegociacion", "NEGOCIADA")
                        .set("fechaNegociacionFactura", LocalDateTime.now())
                        .set("actores", actoresActualizados);


                if (request.getTipoOperacion() != null) {
                    switch (request.getTipoOperacion().getTipo().toUpperCase()) {
                        case "FACTORING" ->  update.set("estadoFirma", "SIN_CESION");
                        case "CONFIRMING" -> update.set("estadoFirma", "SIN_DESCUENTO");
                    }
                }

                mongoTemplate.updateFirst(query, update, Factura.class);

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


        // Construir negociación resumen con estado según resultados
        Negociacion negociacion = new Negociacion();
        negociacion.setTipoOperacion(request.getTipoOperacion());
        negociacion.setRutEmpresa(request.getRutEmpresa());
        //negociacion.setRutPagador("PAGADOR".equalsIgnoreCase(request.getRolEmpresa()) ? request.getRutEmpresa() : null);
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
        if (facturasAceptadas.isEmpty()) estado = "RECHAZADA";
        else if (!facturasRechazadas.isEmpty()) estado = "PARCIAL";
        else estado = "COMPLETADA";

        negociacion.setEstado(estado);
        List<String> allErrores = new ArrayList<>(errores);
        // añadir motivos concretos de facturas rechazadas si quieres
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
        response.setFondeadoresAsignados(negociacion.getRutFondeadores().stream()
                .map(r -> FondeadorAsignacion.builder().rutFondeador(r).nombreFondeador("DESCONOCIDO").build())
                .collect(Collectors.toList()));
        response.setTotalNegociado(totalAceptado);
        response.setEstado(estado);
        response.setErrores(allErrores);

        return response;
    }

    @Override
    @Transactional
    public void aprobarFactura(String idNegociacion, String rutEmpresa, List<AprobacionFacturaRequestDto> facturasDto) {
        Negociacion negociacion = negociacionRepository.findById(idNegociacion)
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        String rolActor = determinarRolActor(rutEmpresa, negociacion);
        if ("DESCONOCIDO".equals(rolActor)) {
            throw new IllegalArgumentException("La empresa con RUT " + rutEmpresa + " no participa en esta negociación");
        }

        boolean esAprobacionTotal = facturasDto.size() == negociacion.getFacturas().size();
        log.info("📄 Modo de aprobación: {}", esAprobacionTotal ? "TOTAL" : "PARCIAL");

        // 🔹 Procesar aprobaciones factura a factura
        for (AprobacionFacturaRequestDto fa : facturasDto) {
            negociacion.getFacturas().stream()
                    .filter(f -> f.getNumeroFactura().equals(fa.getNumeroFactura()))
                    .findFirst()
                    .ifPresent(f -> f.getAprobaciones().add(new AprobacionActorDto(
                            rutEmpresa,
                            fa.isAprobado(),
                            fa.getObservacion(),
                            LocalDateTime.now()
                    )));

            // 💾 Registrar la aprobación en Mongo (colección aprobaciones_negociacion)
            AprobacionNegociacion aprobacion = AprobacionNegociacion.builder()
                    .negociacionId(idNegociacion)
                    .rutEmpresa(rutEmpresa)
                    .rol(rolActor)
                    .aprobado(fa.isAprobado())
                    .fecha(LocalDateTime.now())
                    .build();

            aprobacionNegociacionRepository.save(aprobacion);

            // ⚙️ Si es aprobación parcial → actualizar solo las facturas incluidas
            if (!esAprobacionTotal) {
                facturaService.actualizarEstadoFirma(
                        fa.getNumeroFactura(),
                        fa.isAprobado() ? "APROBADA_PARCIAL" : "RECHAZADA"
                );
            }
        }

        // Evaluar resultados generales solo si es aprobación total
        if (esAprobacionTotal) {
            boolean fondeadorAprobado = actorAprobo("FONDEADOR", negociacion);
            boolean pagadorAprobado = actorAprobo("PAGADOR", negociacion);
            boolean proveedorAprobado = actorAprobo("PROVEEDOR", negociacion);
            boolean hayRechazos = algunoRechazo(negociacion);

            String tipoOperacion = negociacion.getTipoOperacion().getTipo();

            switch (tipoOperacion) {
                case "CONFIRMING" -> {
                    if (hayRechazos) {
                        negociacion.setEstado("RECHAZADA");
                        negociacion.getFacturas().forEach(f ->
                                facturaService.actualizarEstadoFirma(f.getNumeroFactura(), "RECHAZADA"));
                    } else if (fondeadorAprobado && proveedorAprobado) {
                        negociacion.setEstado("APROBADA");
                        negociacion.getFacturas().forEach(f ->
                                facturaService.actualizarEstadoFirma(f.getNumeroFactura(), "CESION"));
                    } else {
                        negociacion.setEstado("PENDIENTE");
                    }
                }

                case "FACTORING" -> {
                    if (hayRechazos) {
                        negociacion.setEstado("RECHAZADA");
                        negociacion.getFacturas().forEach(f ->
                                facturaService.actualizarEstadoFirma(f.getNumeroFactura(), "RECHAZADA"));
                    } else if (fondeadorAprobado && pagadorAprobado) {
                        negociacion.setEstado("APROBADA");
                        negociacion.getFacturas().forEach(f ->
                                facturaService.actualizarEstadoFirma(f.getNumeroFactura(), "PENDIENTE_POR_ENDOSO"));
                    } else {
                        negociacion.setEstado("PENDIENTE");
                    }
                }

                default -> throw new IllegalArgumentException("Tipo de operación desconocido: " + tipoOperacion);
            }
        } else {
            // Si es aprobación parcial → mantener estado global pendiente
            negociacion.setEstado("PENDIENTE");
        }

        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionRepository.save(negociacion);
        log.info("Aprobaciones procesadas correctamente para negociación {}", idNegociacion);
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


    private boolean actorAprobo(String rol, Negociacion negociacion) {
        return negociacion.getFacturas().stream()
                .flatMap(f -> f.getAprobaciones().stream())
                .filter(a -> a.getRutActor() != null)
                .anyMatch(a -> determinarRolActor(a.getRutActor(), negociacion).equals(rol) && a.isAprobado());
    }

    private boolean algunoRechazo(Negociacion negociacion) {
        return negociacion.getFacturas().stream()
                .flatMap(f -> f.getAprobaciones().stream())
                .anyMatch(a -> !a.isAprobado());
    }


}
