package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.util.VigenciaDescuentoValidator;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ProcesarFacturaNegociacionService}.
 */
@Log
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
    private final PayerFundingVisibilityOutputPort payerFundingVisibilityPort;
    private final IndirectFunderOfferOutputPort indirectFunderOfferPort;
    private final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort;
    private final ClockPort clock;

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
            throw new ReglaDeNegocioException(validacion.getMensaje());
        }

        // ==========================================
        // VALIDAR ESTADO
        // ==========================================

        if (!"SIN_NEGOCIAR".equalsIgnoreCase(factura.getEstadoNegociacion())) {
            throw new ReglaDeNegocioException("Factura no negociable estado=" + factura.getEstadoNegociacion());
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
            throw new ReglaDeNegocioException(
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
            throw new ReglaDeNegocioException("Empresa sin cupo");
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

        // saveAndFlush (no save): fuerza que el conflicto de @Version se detecte acá
        // mismo, no al hacer commit de la transacción (donde ya no se puede traducir
        // a una excepción de negocio legible por el cliente).
        try {
            empresaQuotaPort.saveAndFlush(empresaQuota);
        } catch (OptimisticLockConflictException e) {
            throw new ConflictoException(
                    "El cupo de la empresa fue modificado por otra negociación concurrente. "
                            + "Vuelve a intentar.",
                    "CUPO_EMPRESA_CONCURRENCIA");
        }

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
                    .orElseThrow(() -> new RuntimeException(
                            "La empresa fondeadora con NIT " + fon.getRutFondeador()
                                    + " no está registrada en la plataforma"));

            Long supplierId = companyPort
                    .findByNit(factura.getRutProveedor())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RuntimeException(
                            "La empresa proveedora con NIT " + factura.getRutProveedor()
                                    + " no está registrada en la plataforma"));

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RuntimeException(
                            "La empresa pagadora con NIT " + factura.getRutPagador()
                                    + " no está registrada en la plataforma"));

            FondeadorQuota quota;

            if ("FACTORING".equalsIgnoreCase(
                    request.getTipoOperacion().getTipo())) {

                Optional<FondeadorQuota> asignacionDirecta = fondeadorQuotaPort
                        .findByFunderIdAndSupplierIdActivo(funderId, supplierId);

                if (asignacionDirecta.isPresent()) {

                    quota = asignacionDirecta.get();

                    validarFondeadorAprobado(quota);
                    validarVigenciaDescuento(quota, null, factura.getFechavencimientoFactura());

                    if (quota.getSupplierCurrentQuota()
                            .compareTo(valorFactura) < 0) {

                        throw new ReglaDeNegocioException(
                                "Fondeador sin cupo factoring");
                    }

                } else {

                    validarFondeoIndirecto(fon, funderId, payerId, supplierId, valorFactura);
                }

            } else {

                quota = fondeadorQuotaPort
                        .findByFunderIdAndPayerIdActivo(
                                funderId,
                                payerId)
                        .orElseThrow(() -> new RuntimeException(
                                "El fondeador " + fon.getRutFondeador()
                                        + " no tiene una asignación de cupo activa para este pagador"));

                validarFondeadorAprobado(quota);
                validarVigenciaDescuento(quota, null, factura.getFechavencimientoFactura());

                if (quota.getPayerCurrentQuota()
                        .compareTo(valorFactura) < 0) {

                    throw new ReglaDeNegocioException(
                            "Fondeador sin cupo confirming");
                }
            }
        }
    }

    // Al negociar, la factura todavía no tiene fechaNegociacionFactura (recién
    // se setea al final de procesarFactura) — por eso acá solo puede bloquear
    // DIAS_ANTES_VENCIMIENTO; PRIMEROS_DIAS es trivialmente válido en este
    // punto (la ventana recién arrancaría hoy). El chequeo real de
    // PRIMEROS_DIAS ocurre al endosar, contra la fecha ya persistida (ver
    // EndosoDescuentoServiceImpl).
    private void validarVigenciaDescuento(FondeadorQuota quota, LocalDate fechaNegociacion, LocalDate fechaVencimiento) {
        boolean dentroDeVigencia = VigenciaDescuentoValidator.dentroDeVigencia(
                clock.now().toLocalDate(), fechaNegociacion, fechaVencimiento,
                quota.getVigenciaDescuento(), quota.getDiasVigencia());

        if (!dentroDeVigencia) {
            throw new ReglaDeNegocioException(
                    "La vigencia del descuento configurada por el fondeador ya no permite negociar esta factura");
        }
    }

    private void validarFondeadorAprobado(FondeadorQuota quota) {

        boolean estadoAprobado = "APROBADO".equalsIgnoreCase(quota.getEstado());
        boolean aprobadoPorFondeador = Boolean.TRUE.equals(quota.getAprobadoPorFondeador());

        if (!estadoAprobado || !aprobadoPorFondeador) {
            throw new ReglaDeNegocioException(
                    "La asignación de fondeo no está aprobada");
        }
    }

    /**
     * Fallback de "factoring indirecto vía pagador": cuando el proveedor no
     * tiene una asignación directa con el fondeador, se valida si su pagador
     * habilitó el toggle general y tiene una oferta de cupo compartido
     * aprobada con ese fondeador. El cupo por proveedor no se reparte: si
     * este proveedor todavía no negoció contra esta oferta, se compara contra
     * el cupo íntegro de la oferta (sin persistir nada todavía; la fila de
     * consumo se crea recién al descontar, para no dejar filas huérfanas de
     * negociaciones que fallen después por otra razón).
     */
    private void validarFondeoIndirecto(
            FondeadorAsignacion fon,
            Long funderId,
            Long payerId,
            Long supplierId,
            BigDecimal valorFactura
    ) {

        if (!payerFundingVisibilityPort.estaHabilitado(payerId)) {
            throw new ReglaDeNegocioException(
                    "El fondeador " + fon.getRutFondeador()
                            + " no tiene una asignación de cupo activa para este proveedor");
        }

        IndirectFunderOffer oferta = indirectFunderOfferPort
                .findActivaAprobada(funderId, payerId)
                .orElseThrow(() -> new RuntimeException(
                        "El fondeador " + fon.getRutFondeador()
                                + " no tiene una asignación de cupo activa para este proveedor"));

        BigDecimal cupoDisponible = indirectFunderOfferSupplierQuotaPort
                .find(oferta.getId(), supplierId)
                .map(IndirectFunderOfferSupplierQuota::getCurrentQuota)
                .orElse(oferta.getSupplierQuotaTemplate());

        if (cupoDisponible.compareTo(valorFactura) < 0) {
            throw new ReglaDeNegocioException(
                    "Fondeador sin cupo factoring (indirecto vía pagador)");
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
                    .orElseThrow(() -> new RuntimeException(
                            "La empresa fondeadora con NIT " + fon.getRutFondeador()
                                    + " no está registrada en la plataforma"));

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RuntimeException(
                            "La empresa pagadora con NIT " + factura.getRutPagador()
                                    + " no está registrada en la plataforma"));

            if ("FACTORING".equalsIgnoreCase(request.getTipoOperacion().getTipo())) {

                Long supplierId = companyPort
                        .findByNit(factura.getRutProveedor())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RuntimeException(
                                "La empresa proveedora con NIT " + factura.getRutProveedor()
                                        + " no está registrada en la plataforma"));

                Optional<FondeadorQuota> asignacionDirecta = fondeadorQuotaPort
                        .findByFunderIdAndSupplierId(funderId, supplierId);

                if (asignacionDirecta.isPresent()) {

                    FondeadorQuota quota = asignacionDirecta.get();
                    quota.setSupplierCurrentQuota(quota.getSupplierCurrentQuota().subtract(valorFactura));
                    guardarCupoFondeadorConVersionado(quota, fon.getRutFondeador());

                } else {

                    descontarCupoIndirecto(funderId, payerId, supplierId, valorFactura);
                }

            } else {

                FondeadorQuota quota =
                        fondeadorQuotaPort
                                .findByFunderIdAndPayerId(
                                        funderId,
                                        payerId)
                                .orElseThrow(() -> new RuntimeException(
                                        "El fondeador " + fon.getRutFondeador()
                                                + " no tiene una asignación de cupo para este pagador"));

                quota.setPayerCurrentQuota(
                        quota.getPayerCurrentQuota()
                                .subtract(valorFactura));

                guardarCupoFondeadorConVersionado(quota, fon.getRutFondeador());
            }
        }
    }

    /**
     * Guarda el cupo del fondeador forzando flush inmediato (para que un conflicto
     * de {@code @Version} se detecte acá, no al hacer commit de la transacción) y
     * traduce la pérdida de la carrera a una excepción de negocio distinguible de
     * "sin cupo" — el frontend debe poder ofrecer "reintentar" en vez de "cupo
     * insuficiente".
     */
    private void guardarCupoFondeadorConVersionado(FondeadorQuota quota, String rutFondeador) {
        try {
            fondeadorQuotaPort.saveAndFlush(quota);
        } catch (OptimisticLockConflictException e) {
            throw new ConflictoException(
                    "El cupo del fondeador " + rutFondeador
                            + " fue modificado por otra negociación concurrente. Vuelve a intentar.",
                    "CUPO_FONDEADOR_CONCURRENCIA");
        }
    }

    /**
     * Descuenta el cupo de la fila de consumo del proveedor contra una oferta
     * de cupo compartido fondeador→pagador, creándola perezosamente si es la
     * primera vez que este proveedor negocia contra ella (snapshot de
     * tasa/cupo vigentes de la oferta en ese instante). El cupo íntegro de la
     * oferta ({@code supplierQuotaTemplate}) queda fijo como techo, nunca se
     * descuenta ahí.
     *
     * <p>El descuento es un {@code UPDATE ... SET current_quota = current_quota -
     * valor} atómico (no leer-modificar-escribir): dos negociaciones concurrentes
     * del mismo par ya no pueden pisarse el resultado. Si la fila todavía no
     * existe ({@code descontarCupo} devuelve 0), se crea con el snapshot vigente
     * y se reintenta el descuento; una creación concurrente que gane la carrera
     * del UNIQUE se traduce a {@link ConflictoException} en el adapter y se
     * absorbe acá — la fila ya existe, el reintento la descuenta.</p>
     */
    private void descontarCupoIndirecto(Long funderId, Long payerId, Long supplierId, BigDecimal valorFactura) {

        IndirectFunderOffer oferta = indirectFunderOfferPort
                .findActivaAprobada(funderId, payerId)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró la oferta de fondeo indirecta al descontar cupo"));

        int filas = indirectFunderOfferSupplierQuotaPort.descontarCupo(oferta.getId(), supplierId, valorFactura);
        if (filas > 0) {
            return;
        }

        try {
            indirectFunderOfferSupplierQuotaPort.crear(
                    oferta.getId(), supplierId, oferta.getPayerRate(), oferta.getSupplierQuotaTemplate());
        } catch (ConflictoException e) {
            // Otra negociación concurrente del mismo par creó la fila primero.
            // No es un error: existe, el descuento de abajo la alcanza.
            log.fine(() -> "Fila de consumo del par (oferta=" + oferta.getId() + ", proveedor=" + supplierId
                    + ") creada por una negociación concurrente; se reintenta el descuento.");
        }

        filas = indirectFunderOfferSupplierQuotaPort.descontarCupo(oferta.getId(), supplierId, valorFactura);
        if (filas == 0) {
            throw new ConflictoException(
                    "No se pudo registrar el consumo de cupo del proveedor contra la oferta de fondeo "
                            + "indirecta. Vuelve a intentar.",
                    "CUPO_PROVEEDOR_CONCURRENCIA");
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

                throw new ReglaDeNegocioException(
                        "Solo proveedor puede hacer factoring");
            }

        } else {

            String pagador =
                    normalizarNit(factura.getRutPagador());

            if (!empresa.equals(pagador)) {

                throw new ReglaDeNegocioException(
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
                .toUpperCase(Locale.ROOT);
    }
}

