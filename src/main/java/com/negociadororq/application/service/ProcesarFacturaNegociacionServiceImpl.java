package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.negociadororq.application.dto.DetalleCupoIndirectoDto;
import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.CupoIndirectoInsuficienteException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.util.Pesos;
import com.negociadororq.application.util.VigenciaDescuentoValidator;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.DescuentoCupos;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;
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

    /** Fase 28d (A5b): la factura ya tiene una negociación viva. */
    public static final String FACTURA_CON_NEGOCIACION_VIVA = "FACTURA_CON_NEGOCIACION_VIVA";
    private static final java.util.Set<String> ESTADOS_CON_NEGOCIACION_VIVA = java.util.Set.of("NEGOCIADA", "ENDOSADA");

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
    /** Fase 28d (A2): registro exacto de lo descontado ({@code operacion_pendiente}), en la misma transacción. */
    private final OperacionPendienteOutputPort operacionPendientePort;
    /** Fase 28d (A2): auditoría CUPOS_DESCONTADOS, en la misma transacción que los descuentos (outbox JDBC). */
    private final AuditoriaNegociacionService auditoria;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada reqFact,
            String negociacionId) {
        return procesarFactura(request, reqFact, negociacionId, ContextoCupoIndirecto.vacio());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada reqFact,
            String negociacionId, ContextoCupoIndirecto cupoIndirecto) {

        // Fase 28d (C): la solicitud ya trae el CUFE y los RUT de la base (resueltos en crearNegociacion).
        Factura factura = new ResolutorFactura(facturaPort).resuelta(reqFact);

        // ==========================================
        // VALIDAR VENCIMIENTO
        // ==========================================

        ValidacionFacturaResponseDto validacion =
                validacionFacturaService.validarFacturaVencida(factura, null);

        if (!validacion.isValida()) {
            throw new ReglaDeNegocioException(validacion.getMensaje());
        }

        // ==========================================
        // VALIDAR ESTADO
        // ==========================================

        exigirNegociable(factura);

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
                        new RecursoNoEncontradoException("Operador no existe"));

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
                        new RecursoNoEncontradoException("Empresa no encontrada"));

        EmpresaQuota empresaQuota =
                empresaQuotaPort.findByCompanyId(companyId)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException("EmpresaQuota no encontrada"));

        if (empresaQuota.getCurrentQuota().compareTo(valorFactura) < 0) {
            throw new ReglaDeNegocioException("Empresa sin cupo");
        }

        // ==========================================
        // FONDEADORES
        // ==========================================

        List<FondeadorAsignacion> fondeadores = obtenerFondeadores(request, reqFact);

        validarFondeadores(request, factura, fondeadores, valorFactura, cupoIndirecto);

        // ==========================================
        // DESCONTAR CUPOS
        // ==========================================

        // Fase 28d (A2): cada descuento, con su id y el monto exacto, para devolver exactamente eso al revertir.
        List<DescuentoCupos.Descuento> descuentos = new ArrayList<>();

        cupoConsumidorService.descontarCupo(userId, valorFactura);
        descuentos.add(new DescuentoCupos.Descuento(DescuentoCupos.Tipo.OPERADOR, userId, null, valorFactura));

        empresaQuota.setCurrentQuota(empresaQuota.getCurrentQuota().subtract(valorFactura));
        descuentos.add(new DescuentoCupos.Descuento(DescuentoCupos.Tipo.EMPRESA, companyId, null, valorFactura));

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

        descontarCupoFondeadores(request, factura, fondeadores, valorFactura, descuentos, cupoIndirecto);

        // Fase 28d (A2): el registro va en la MISMA transacción que los descuentos (REQUIRES_NEW de esta factura): o se
        // confirman juntos o ninguno. Una clave repetida (la misma factura dos veces en la negociación) revierte todo.
        String tipoOperacion = request.getTipoOperacion().getTipo();
        String contraparte = factura.getRutProveedor();
        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            contraparte = factura.getRutPagador();
        }
        DescuentoCupos.Contexto contexto = new DescuentoCupos.Contexto(tipoOperacion.toUpperCase(Locale.ROOT),
                request.getRutEmpresa(), contraparte, factura.getNumeroFactura());
        if (!operacionPendientePort.registrar(TipoOperacionPendiente.CREAR_NEGOCIACION,
                OperacionPendiente.clave(negociacionId, factura.getCufe()), ReversionExactaService.CUPOS_DESCONTADOS,
                new DescuentoCupos(descuentos, contexto).aJson())) {
            throw new ConflictoException("La factura ya está registrada en esta negociación.", "FACTURA_REPETIDA");
        }
        auditoria.cuposDescontados(negociacionId, factura.getCufe(), factura.getNumeroFactura(), descuentos.size());

        // Fase 28d (A2): la factura se marca NEGOCIADA en la transacción Mongo de la creación (marcarComoNegociada),
        // junto con la negociación y su marcador; aquí solo se toca MySQL.

        // ==========================================
        // RESPUESTA
        // ==========================================

        reqFact.setFondeadores(fondeadores);
        reqFact.setCufe(factura.getCufe());
        reqFact.setRutProveedor(factura.getRutProveedor());
        reqFact.setRutPagador(factura.getRutPagador());
        reqFact.setValorFactura(factura.getValorFactura());

        return reqFact;
    }

    /**
     * La factura solo se negocia desde SIN_NEGOCIAR y sin otra negociación viva (Fase 28d, A5b).
     */
    private void exigirNegociable(Factura factura) {
        // Fase 28d (A5b): NEGOCIADA o ENDOSADA es una negociación viva (invariante: una factura con negociación viva nunca
        // está SIN_NEGOCIAR); el cliente recibe el código propio. Los demás estados (VENCIDA, RECHAZADA, LIQUIDADA...)
        // siguen como "no negociable".
        if (ESTADOS_CON_NEGOCIACION_VIVA.contains(factura.getEstadoNegociacion())) {
            throw new ReglaDeNegocioException(FACTURA_CON_NEGOCIACION_VIVA + ": la factura ya tiene una negociación en "
                    + "curso. Factura no negociable estado=" + factura.getEstadoNegociacion(), FACTURA_CON_NEGOCIACION_VIVA);
        }
        if (!"SIN_NEGOCIAR".equalsIgnoreCase(factura.getEstadoNegociacion())) {
            throw new ReglaDeNegocioException("Factura no negociable estado=" + factura.getEstadoNegociacion());
        }
        // Fase 28d (A5b): verificación rápida por registro, antes de descontar cupos (la guarda definitiva es el
        // compare-and-set de marcarComoNegociada en la transacción Mongo).
        if (factura.getCufe() != null && operacionPendientePort.existeCreacionViva(factura.getCufe())) {
            throw new ReglaDeNegocioException(FACTURA_CON_NEGOCIACION_VIVA + ": la factura ya tiene una negociación en curso",
                    FACTURA_CON_NEGOCIACION_VIVA);
        }
    }

    @Override
    public void marcarComoNegociada(NegociacionRequestDto request, FacturaNegociada procesada) {
        Factura factura = new ResolutorFactura(facturaPort).resuelta(procesada);
        List<FondeadorAsignacion> fondeadores = Optional.ofNullable(procesada.getFondeadores())
                .orElse(Collections.emptyList());
        if (!facturaPatchPort.marcarComoNegociada(factura.getId(),
                calcularEstadoFirma(request.getTipoOperacion().getTipo()), construirActores(factura, fondeadores))) {
            // Fase 28d (A5b): otra negociación tomó la factura entre la lectura y esta transacción: se aborta toda la
            // creación (la transacción Mongo se deshace y quien llama devuelve los cupos exactos, sin plantilla A).
            throw new ConflictoException(FACTURA_CON_NEGOCIACION_VIVA + ": la factura " + factura.getNumeroFactura()
                    + " ya tiene una negociación en curso; no se creó la negociación. Puede volver a intentarlo.",
                    FACTURA_CON_NEGOCIACION_VIVA);
        }
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
            BigDecimal valorFactura,
            ContextoCupoIndirecto contexto
    ) {

        for (FondeadorAsignacion fon : fondeadores) {

            Long funderId = companyPort
                    .findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La empresa fondeadora con NIT " + fon.getRutFondeador()
                                    + " no está registrada en la plataforma"));

            Long supplierId = companyPort
                    .findByNit(factura.getRutProveedor())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La empresa proveedora con NIT " + factura.getRutProveedor()
                                    + " no está registrada en la plataforma"));

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
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

                    validarFondeoIndirecto(fon, factura, new Trio(funderId, payerId, supplierId), valorFactura,
                            contexto);
                }

            } else {

                quota = fondeadorQuotaPort
                        .findByFunderIdAndPayerIdActivo(
                                funderId,
                                payerId)
                        .orElseThrow(() -> new ReglaDeNegocioException(
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
            Factura factura,
            Trio trio,
            BigDecimal valorFactura,
            ContextoCupoIndirecto contexto
    ) {

        IndirectFunderOffer oferta = ofertaIndirecta(trio.funderId(), trio.payerId())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "El fondeador " + fon.getRutFondeador()
                                + " no tiene una asignación de cupo activa para este proveedor"));

        // Fase 28d (validación acumulada, decisión de John): lo que queda al llegar a ESTA factura. Las anteriores de la
        // petición ya confirmaron su transacción (cada factura va en la suya, en el orden de la petición), así que su
        // consumo ya está descontado aquí. La sentencia atómica del descuento sigue siendo la barrera final.
        BigDecimal cupoDisponible = disponibleIndirecto(oferta, trio.supplierId());

        if (cupoDisponible.compareTo(valorFactura) < 0) {
            throw sinCupoIndirecto(factura, fon.getRutFondeador(), trio, cupoDisponible, valorFactura, contexto);
        }
    }

    /** El pagador habilitó el cupo indirecto y hay una oferta activa y aprobada del fondeador hacia él. */
    private Optional<IndirectFunderOffer> ofertaIndirecta(Long funderId, Long payerId) {
        if (!payerFundingVisibilityPort.estaHabilitado(payerId)) {
            return Optional.empty();
        }
        return indirectFunderOfferPort.findActivaAprobada(funderId, payerId);
    }

    /** Lo disponible del proveedor contra la oferta: su fila de consumo o, si nunca negoció, el cupo íntegro. */
    private BigDecimal disponibleIndirecto(IndirectFunderOffer oferta, Long supplierId) {
        return indirectFunderOfferSupplierQuotaPort
                .find(oferta.getId(), supplierId)
                .map(IndirectFunderOfferSupplierQuota::getCurrentQuota)
                .orElse(oferta.getSupplierQuotaTemplate());
    }

    /** Fondeador, pagador y proveedor de un cupo indirecto. */
    private record Trio(Long funderId, Long payerId, Long supplierId) {
        String clave() {
            return ContextoCupoIndirecto.clave(funderId, payerId, supplierId);
        }
    }

    @Override
    public ContextoCupoIndirecto prepararCupoIndirecto(NegociacionRequestDto request, List<Factura> facturasBd) {
        ContextoCupoIndirecto.Constructor ctx = new ContextoCupoIndirecto.Constructor();
        if (request.getTipoOperacion() == null
                || !"FACTORING".equalsIgnoreCase(request.getTipoOperacion().getTipo())) {
            return ctx.construir();
        }
        for (int i = 0; i < facturasBd.size(); i++) {
            Factura factura = facturasBd.get(i);
            BigDecimal valor = Optional.ofNullable(factura.getValorNetoFactura())
                    .or(() -> Optional.ofNullable(factura.getValorFactura())).orElse(BigDecimal.ZERO);
            for (FondeadorAsignacion fon : obtenerFondeadores(request, request.getFacturas().get(i))) {
                trioIndirecto(fon, factura).ifPresent(t -> ofertaIndirecta(t.funderId(), t.payerId())
                        .ifPresent(oferta -> ctx.sumar(t.clave(), valor, disponibleIndirecto(oferta, t.supplierId()))));
            }
        }
        return ctx.construir();
    }

    /** El trío de la factura con ese fondeador, si va por cupo indirecto (sin asignación directa activa). */
    private Optional<Trio> trioIndirecto(FondeadorAsignacion fon, Factura factura) {
        Optional<Long> funderId = companyPort.findByNit(fon.getRutFondeador()).map(Company::getIdeCompany);
        Optional<Long> supplierId = companyPort.findByNit(factura.getRutProveedor()).map(Company::getIdeCompany);
        Optional<Long> payerId = companyPort.findByNit(factura.getRutPagador()).map(Company::getIdeCompany);
        if (funderId.isEmpty() || supplierId.isEmpty() || payerId.isEmpty()
                || fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(funderId.get(), supplierId.get()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new Trio(funderId.get(), payerId.get(), supplierId.get()));
    }

    /**
     * Fase 28d (decisión de John; texto de John: el cupo lo define el FONDEADOR en su oferta a la pagadora, igual para cada
     * proveedor): el rechazo de una factura que no cabe en el cupo indirecto, con los montos exactos, los
     * nombres del pagador y del fondeador y, si la petición los tiene, la suma del trío y su disponible inicial.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private CupoIndirectoInsuficienteException sinCupoIndirecto(Factura factura, String rutFondeador, Trio trio,
            BigDecimal disponible, BigDecimal requerido, ContextoCupoIndirecto contexto) {
        BigDecimal faltante = requerido.subtract(disponible);
        String fondeador = nombreEmpresa(rutFondeador);
        String pagador = nombreEmpresa(factura.getRutPagador());
        Optional<ContextoCupoIndirecto.Acumulado> acumulado = contexto.de(trio.clave());
        DetalleCupoIndirectoDto detalle = DetalleCupoIndirectoDto.builder()
                .rutFondeador(rutFondeador).fondeador(fondeador)
                .rutPagador(factura.getRutPagador()).pagador(pagador)
                .disponible(disponible).requerido(requerido).faltante(faltante)
                .sumaPeticion(acumulado.map(ContextoCupoIndirecto.Acumulado::sumaPeticion).orElse(null))
                .disponibleInicial(acumulado.map(ContextoCupoIndirecto.Acumulado::disponibleInicial).orElse(null))
                .build();
        StringBuilder mensaje = new StringBuilder("La factura ").append(factura.getNumeroFactura()).append(" por ")
                .append(Pesos.formato(requerido)).append(" no se incluyó: el cupo de factoring que ").append(fondeador)
                .append(" ofrece a los proveedores de ").append(pagador).append(" tiene ").append(Pesos.formato(disponible))
                .append(" disponibles para ti; faltan ").append(Pesos.formato(faltante)).append(". Puedes pedirle a ")
                .append(fondeador).append(" que recargue o amplíe tu cupo.");
        acumulado.filter(a -> a.sumaPeticion().compareTo(requerido) > 0).ifPresent(a -> mensaje
                .append(" Las facturas de esta petición con ese cupo suman ").append(Pesos.formato(a.sumaPeticion()))
                .append(" y al empezar había ").append(Pesos.formato(a.disponibleInicial()))
                .append(" disponibles; entran en el orden de la petición."));
        return new CupoIndirectoInsuficienteException(mensaje.toString(), detalle);
    }

    private String nombreEmpresa(String nit) {
        return companyPort.findByNit(nit).map(Company::getNameCompany).filter(n -> !n.isBlank()).orElse(nit);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void descontarCupoFondeadores(
            NegociacionRequestDto request,
            Factura factura,
            List<FondeadorAsignacion> fondeadores,
            BigDecimal valorFactura,
            List<DescuentoCupos.Descuento> descuentos,
            ContextoCupoIndirecto contexto
    ) {

        for (FondeadorAsignacion fon : fondeadores) {

            Long funderId = companyPort
                    .findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La empresa fondeadora con NIT " + fon.getRutFondeador()
                                    + " no está registrada en la plataforma"));

            Long payerId = companyPort
                    .findByNit(factura.getRutPagador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La empresa pagadora con NIT " + factura.getRutPagador()
                                    + " no está registrada en la plataforma"));

            if ("FACTORING".equalsIgnoreCase(request.getTipoOperacion().getTipo())) {

                Long supplierId = companyPort
                        .findByNit(factura.getRutProveedor())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "La empresa proveedora con NIT " + factura.getRutProveedor()
                                        + " no está registrada en la plataforma"));

                Optional<FondeadorQuota> asignacionDirecta = fondeadorQuotaPort
                        .findByFunderIdAndSupplierId(funderId, supplierId);

                if (asignacionDirecta.isPresent()) {

                    FondeadorQuota quota = asignacionDirecta.get();
                    quota.setSupplierCurrentQuota(quota.getSupplierCurrentQuota().subtract(valorFactura));
                    guardarCupoFondeadorConVersionado(quota, fon.getRutFondeador());
                    descuentos.add(new DescuentoCupos.Descuento(DescuentoCupos.Tipo.FONDEADOR_FACTORING, funderId,
                            supplierId, valorFactura));

                } else {

                    Long ofertaId = descontarCupoIndirecto(fon, factura, new Trio(funderId, payerId, supplierId),
                            valorFactura, contexto);
                    descuentos.add(new DescuentoCupos.Descuento(DescuentoCupos.Tipo.INDIRECTO, ofertaId, supplierId,
                            valorFactura));
                }

            } else {

                FondeadorQuota quota =
                        fondeadorQuotaPort
                                .findByFunderIdAndPayerId(
                                        funderId,
                                        payerId)
                                .orElseThrow(() -> new ReglaDeNegocioException(
                                        "El fondeador " + fon.getRutFondeador()
                                                + " no tiene una asignación de cupo para este pagador"));

                quota.setPayerCurrentQuota(
                        quota.getPayerCurrentQuota()
                                .subtract(valorFactura));

                guardarCupoFondeadorConVersionado(quota, fon.getRutFondeador());
                descuentos.add(new DescuentoCupos.Descuento(DescuentoCupos.Tipo.FONDEADOR_CONFIRMING, funderId,
                        payerId, valorFactura));
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
    private Long descontarCupoIndirecto(FondeadorAsignacion fon, Factura factura, Trio trio, BigDecimal valorFactura,
            ContextoCupoIndirecto contexto) {
        Long funderId = trio.funderId();
        Long payerId = trio.payerId();
        Long supplierId = trio.supplierId();

        IndirectFunderOffer oferta = indirectFunderOfferPort
                .findActivaAprobada(funderId, payerId)
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "No se encontró la oferta de fondeo indirecta al descontar cupo"));

        int filas = indirectFunderOfferSupplierQuotaPort.descontarCupo(oferta.getId(), supplierId, valorFactura);
        if (filas > 0) {
            return oferta.getId();
        }
        // Fase 28d (A5b): 0 filas con la fila ya creada es cupo insuficiente (la sentencia lo exige); sin fila, se crea.
        // Una concurrente consumió el cupo entre la validación y el descuento: el rechazo lleva lo que queda AHORA.
        Optional<IndirectFunderOfferSupplierQuota> fila = indirectFunderOfferSupplierQuotaPort.find(oferta.getId(), supplierId);
        if (fila.isPresent()) {
            throw sinCupoIndirecto(factura, fon.getRutFondeador(), trio, fila.get().getCurrentQuota(), valorFactura,
                    contexto);
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
            Optional<IndirectFunderOfferSupplierQuota> creada =
                    indirectFunderOfferSupplierQuotaPort.find(oferta.getId(), supplierId);
            if (creada.isPresent()) {
                throw sinCupoIndirecto(factura, fon.getRutFondeador(), trio, creada.get().getCurrentQuota(), valorFactura,
                        contexto);
            }
        }
        if (filas == 0) {
            throw new ConflictoException(
                    "No se pudo registrar el consumo de cupo del proveedor contra la oferta de fondeo "
                            + "indirecta. Vuelve a intentar.",
                    "CUPO_PROVEEDOR_CONCURRENCIA");
        }
        return oferta.getId();
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

