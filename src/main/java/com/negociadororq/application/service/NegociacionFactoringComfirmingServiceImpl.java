package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.negociadororq.application.dto.DetalleCupoIndirectoDto;
import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;
import com.negociadororq.application.dto.RechazoFacturaDto;
import com.negociadororq.application.exception.AccionNoPermitidaException;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.CupoIndirectoInsuficienteException;
import com.negociadororq.application.exception.NegociadorException;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOperationOutputPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.MarcaNegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.application.port.output.TransaccionMongoOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Implementación de {@link NegociacionFactoringComfirmingService}.
 */
@Log
@RequiredArgsConstructor
public class NegociacionFactoringComfirmingServiceImpl implements NegociacionFactoringComfirmingService {

    /** Motivo de rechazo de una factura cuando el error no es del dominio: el detalle queda en el log. */
    static final String MENSAJE_ERROR_FACTURA = "No se pudo procesar la factura por un error interno.";

    // Clases de operación de cada tipo: las mismas para las que ms-liquidador-neg tiene
    // calculadora (GET /liquidar/clases). Una clase de otro tipo se negociaba y fallaba
    // recién al liquidar.
    private static final Map<String, List<String>> CLASES_POR_TIPO = Map.of(
            "FACTORING", List.of("ANTICIPO", "CON_RESPONSABILIDAD", "SIN_DESCUENTO", "SIN_RESPONSABILIDAD"),
            "CONFIRMING", List.of("TRADICIONAL", "PRONTO_PAGO", "SIN_RECURSO", "COSTO_COMPARTIDO", "CON_GARANTIA"));

    private final FacturaOutputPort facturaPort;
    private final NegociacionOutputPort negociacionPort;
    private final UserOutputPort userPort;
    private final CompanyOutputPort companyPort;
    private final CompanyOperationOutputPort companyOperationPort;
    private final CompanySectionVerificationOutputPort companySectionVerificationPort;
    private final NotificacionService notificacionService;
    private final ProcesarFacturaNegociacionService procesarFacturaNegociacionService;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final ClockPort clock;
    private final AuditoriaNegociacionService auditoria;
    /** Fase 28d (A2): transacción Mongo de la creación (marcador, negociación, facturas y correo). */
    private final TransaccionMongoOutputPort transaccionMongo;

    /** Fase 28d (A5b): otra operación modificó el cupo a la vez y la factura no tiene otra negociación viva. */
    public static final String CUPO_CONCURRENCIA_REINTENTAR = "CUPO_CONCURRENCIA_REINTENTAR";
    /** Códigos del bloqueo optimista del cupo de la empresa y del fondeador (ProcesarFacturaNegociacionServiceImpl). */
    static final java.util.Set<String> CODIGOS_CONCURRENCIA_CUPO =
            java.util.Set.of("CUPO_EMPRESA_CONCURRENCIA", "CUPO_FONDEADOR_CONCURRENCIA",
                    // Fase 28d (P-19): deadlock o lock wait timeout traducido en infraestructura (BloqueosProcesarFactura)
                    "CUPO_BLOQUEO_REINTENTAR");
    private static final int INTENTOS_VERIFICACION_VIVA = 5;
    private static final long ESPERA_VERIFICACION_VIVA_MS = 100;
    /** Fase 28d (A2): marcador único por negociación (CREADA o CANCELADA). */
    private final MarcaNegociacionOutputPort marca;
    /** Fase 28d (A2): operaciones CREAR_NEGOCIACION (CUPOS_DESCONTADOS → COMPLETADA). */
    private final OperacionPendienteOutputPort operaciones;
    /** Fase 28d (A2): reversión exacta de lo descontado si la creación se aborta. */
    private final ReversionExactaService reversionExacta;
    /** Fase 28d (punto 1.2c): aviso al fondeador por falta de cupo indirecto (fuera de la transacción de la factura). */
    private final AvisoCupoIndirectoService avisoCupoIndirecto;

    /** Fase 28d (A2): código del 409 de una creación que el reconciliador ya canceló. */
    static final String NEGOCIACION_CANCELADA = "NEGOCIACION_CANCELADA";

    /**
     * Crea una negociación procesando factura a factura.
     * <ul>
     *   <li>Valida cupo operador, empresa y cada fondeador-relación.</li>
     *   <li>Si una factura no tiene cupo con algún actor, se marca como NO_NEGOCIADA
     *       (no se toca su estado en BD).</li>
     *   <li>Las facturas que sí pasan se actualizan y se descuentan cupos correspondientes.</li>
     *   <li>Devuelve en errores la lista de facturas que no se negociaron y la razón.</li>
     * </ul>
     */
    @Override
    public NegociacionResponseDto crearNegociacion(NegociacionRequestDto request) {


        List<String> errores = new ArrayList<>();
        List<FacturaNegociada> facturasAceptadas = new ArrayList<>();
        List<String> facturasRechazadas = new ArrayList<>();

        userPort.findByUsername(request.getRutOperador())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe operador con username " + request.getRutOperador(), "OPERADOR_NO_ENCONTRADO"));

        validarClaseSegunTipo(request);

        // Fase 28d: las contrapartes salen de la BASE (factura por factura), no de lo que manda el cliente, y se
        // validan antes de procesar cualquier factura (antes de reservar cupos).
        // Fase 28d (C): cada factura se resuelve por CUFE o por número + RUT de proveedor y pagador (exactamente una, de
        // la empresa que negocia); desde aquí la solicitud lleva el CUFE y los RUT de la base, no los del cliente.
        ResolutorFactura resolutor = new ResolutorFactura(facturaPort);
        String tipo = null;
        if (request.getTipoOperacion() != null) {
            tipo = request.getTipoOperacion().getTipo();
        }
        List<Factura> facturasBd = new ArrayList<>();
        for (FacturaNegociada fn : request.getFacturas()) {
            Factura factura = resolutor.resolverSolicitud(fn, tipo, request.getRutEmpresa());
            fn.setCufe(factura.getCufe());
            fn.setRutProveedor(factura.getRutProveedor());
            fn.setRutPagador(factura.getRutPagador());
            facturasBd.add(factura);
        }
        Set<String> contrapartes = contrapartesDesdeBd(request, facturasBd);
        validarOperacionHabilitada(request, contrapartes);
        validarUnicaContraparte(request, contrapartes);
        // Fase 28d (validación acumulada del cupo indirecto): suma y disponible inicial de cada trío, antes de la primera
        // factura. Las facturas se procesan después en el orden de la petición (el orden decide qué entra).
        ContextoCupoIndirecto contextoIndirecto = Optional.ofNullable(
                procesarFacturaNegociacionService.prepararCupoIndirecto(request, facturasBd))
                .orElse(ContextoCupoIndirecto.vacio());
        List<RechazoFacturaDto> rechazos = new ArrayList<>();

        /* ======================================================
           1. Agrupar facturas por pagador SOLO en CONFIRMING
        ====================================================== */
        Map<String, List<FacturaNegociada>> facturasPorPagador = new HashMap<>();

        for (int i = 0; i < request.getFacturas().size(); i++) {
            facturasPorPagador
                    .computeIfAbsent(facturasBd.get(i).getRutPagador(), k -> new ArrayList<>())
                    .add(request.getFacturas().get(i));
        }

        // Fase 28d (A2): el id de la negociación se genera ANTES de tocar cupos: es la clave del registro exacto de lo
        // descontado de cada factura (negociacion|cufe), que se escribe en la misma transacción que los descuentos.
        String idNegociacion = negociacionPort.nuevoId();

        for (FacturaNegociada facturaReq : request.getFacturas()) {
            procesarUna(request, facturaReq, idNegociacion, facturasAceptadas, errores, facturasRechazadas, rechazos,
                    contextoIndirecto);
        }


        Set<String> pagadores = facturasPorPagador.keySet();

        // Construir negociación resumen con estado según resultados
        Negociacion negociacion = new Negociacion();
        negociacion.setId(idNegociacion);
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
        } else if (facturasAceptadas.isEmpty()) {
            rutProveedor = null;
        } else {
            rutProveedor = facturasAceptadas.get(0).getRutProveedor();
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
        if (facturasAceptadas.isEmpty()) {
            estado = EstadoNegociacion.RECHAZADA.name();
        } else if (!facturasRechazadas.isEmpty()) {
            estado = EstadoNegociacion.PENDIENTE.name();
        } else {
            estado = EstadoNegociacion.PENDIENTE.name();
        }

        negociacion.setEstado(estado);
        List<String> allErrores = new ArrayList<>(errores);

        // añadir motivos concretos de facturas rechazadas
        negociacion.setErrores(allErrores);
        negociacion.setFechaCreacion(clock.now());
        negociacion.setFechaUltimaActualizacion(clock.now());
        try {
            negociacion = guardarEnMongo(request, negociacion, facturasAceptadas);
        } catch (ConflictoException e) {
            if (ProcesarFacturaNegociacionServiceImpl.FACTURA_CON_NEGOCIACION_VIVA.equals(e.getCodigo())) {
                rechazarPorNegociacionViva(idNegociacion, facturasAceptadas);
            }
            throw e;
        }

        // Fase 28d (A2): el paso Mongo terminó; las operaciones de cada factura quedan COMPLETADA. Si este CAS no llega
        // (caída), el reconciliador ve el marcador CREADA y completa.
        for (FacturaNegociada fn : facturasAceptadas) {
            operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION,
                    OperacionPendiente.clave(idNegociacion, fn.getCufe()), ReversionExactaService.CUPOS_DESCONTADOS,
                    ReversionExactaService.COMPLETADA);
        }
        auditoria.negociacionCreada(negociacion, request, facturasRechazadas.size());

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
        response.setRechazos(rechazos);

        return response;
    }

    /**
     * Rechaza toda la solicitud si la clase de operación no pertenece a su tipo (p. ej.
     * TRADICIONAL, que es de CONFIRMING, en una FACTORING). Un tipo que no es FACTORING ni
     * CONFIRMING lo resuelve validarOperacionHabilitada, como hasta ahora.
     */
    private void validarClaseSegunTipo(NegociacionRequestDto request) {
        if (request.getTipoOperacion() == null || request.getTipoOperacion().getTipo() == null) {
            return;
        }
        String tipo = request.getTipoOperacion().getTipo().toUpperCase(Locale.ROOT);
        List<String> clasesValidas = CLASES_POR_TIPO.get(tipo);
        if (clasesValidas == null) {
            return;
        }
        String clase = request.getTipoOperacion().getClase();
        if (clase == null || !clasesValidas.contains(clase.toUpperCase(Locale.ROOT))) {
            throw new ReglaDeNegocioException(
                    "La clase de operación " + clase + " no es válida para " + tipo
                            + ". Clases válidas: " + String.join(", ", clasesValidas) + ".",
                    "CLASE_OPERACION_INVALIDA");
        }
    }

    /**
     * Rechaza toda la solicitud (no factura a factura) si la empresa solicitante, o cualquiera de
     * las contrapartes involucradas (pagador en FACTORING, proveedor en CONFIRMING), no tiene
     * habilitada la operación (FACTORING/CONFIRMING) que se está intentando ejecutar. A diferencia
     * de validarPermisoOperacion (dentro de ProcesarFacturaNegociacionServiceImpl, que valida
     * factura a factura y cuyas excepciones se absorben como rechazo individual), esta se lanza
     * antes del loop para que el fallo sea un error HTTP real, no una factura rechazada silenciosa.
     */
    private void validarOperacionHabilitada(NegociacionRequestDto request, Set<String> contrapartes) {
        String tipoOperacion = null;
        if (request.getTipoOperacion() != null) {
            tipoOperacion = request.getTipoOperacion().getTipo();
        }
        if (tipoOperacion == null) {
            return;
        }

        String tipoOperacionUpper = tipoOperacion.toUpperCase(Locale.ROOT);

        validarEmpresaHabilitada(request.getRutEmpresa(), tipoOperacionUpper);

        for (String rutContraparte : contrapartes) {
            validarEmpresaHabilitada(rutContraparte, tipoOperacionUpper);
        }
    }

    /**
     * Exige que todas las facturas de la solicitud tengan la misma contraparte: el mismo
     * pagador en FACTORING, o el mismo proveedor en CONFIRMING. Evita mezclar varias
     * contrapartes en una sola negociación (lo que antes dejaba a "rutProveedor"/"rutPagador"
     * a nivel de negociación reflejando solo una de ellas): si el usuario quiere negociar con
     * varias, debe hacerlo en solicitudes separadas, una por contraparte.
     */
    private void validarUnicaContraparte(NegociacionRequestDto request, Set<String> contrapartes) {
        String tipoOperacion = null;
        if (request.getTipoOperacion() != null) {
            tipoOperacion = request.getTipoOperacion().getTipo();
        }
        if (tipoOperacion == null) {
            return;
        }

        boolean esFactoring = "FACTORING".equalsIgnoreCase(tipoOperacion);

        if (contrapartes.size() > 1) {
            String tipoContraparte;
            if (esFactoring) {
                tipoContraparte = "pagador";
            } else {
                tipoContraparte = "proveedor";
            }
            throw new PeticionInvalidaException(
                    "Todas las facturas de una negociación deben tener el mismo " + tipoContraparte
                            + ". Se recibieron facturas de varios " + tipoContraparte + "es distintos: "
                            + String.join(", ", contrapartes) + ". Negocie cada " + tipoContraparte
                            + " por separado.",
                    "MULTIPLES_CONTRAPARTES");
        }
    }

    /**
     * Fase 28d: las contrapartes de la solicitud (pagador en FACTORING, proveedor en CONFIRMING) tal como están en la
     * base, factura por factura. Una factura sin esa contraparte en la base es un error de la solicitud entera.
     */
    private static Set<String> contrapartesDesdeBd(NegociacionRequestDto request, List<Factura> facturasBd) {
        if (request.getTipoOperacion() == null || request.getTipoOperacion().getTipo() == null) {
            return Set.of();
        }
        boolean esFactoring = "FACTORING".equalsIgnoreCase(request.getTipoOperacion().getTipo());
        Set<String> contrapartes = new java.util.LinkedHashSet<>();
        String tipoContraparte;
        if (esFactoring) {
            tipoContraparte = "pagador";
        } else {
            tipoContraparte = "proveedor";
        }
        for (int i = 0; i < request.getFacturas().size(); i++) {
            FacturaNegociada fn = request.getFacturas().get(i);
            Factura factura = facturasBd.get(i);
            String rut;
            if (esFactoring) {
                rut = factura.getRutPagador();
            } else {
                rut = factura.getRutProveedor();
            }
            if (rut == null || rut.isBlank()) {
                throw new PeticionInvalidaException("La factura " + fn.getNumeroFactura() + " no tiene "
                        + tipoContraparte + " registrado.", "FACTURA_SIN_CONTRAPARTE");
            }
            contrapartes.add(rut);
        }
        return contrapartes;
    }

    private void validarEmpresaHabilitada(String rutEmpresa, String tipoOperacionUpper) {
        Company company = companyPort.findByNit(rutEmpresa)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe empresa con NIT " + rutEmpresa, "EMPRESA_NO_ENCONTRADA"));

        if (!companyOperationPort.isEnabled(company.getIdeCompany(), tipoOperacionUpper)) {
            throw new AccionNoPermitidaException(
                    "La empresa con NIT " + rutEmpresa + " no tiene habilitada la operación "
                            + tipoOperacionUpper + ".",
                    "OPERACION_NO_HABILITADA");
        }

        validarTesoreriaVerificadaSiCalifica(company, rutEmpresa);
    }

    /**
     * Una empresa fondeadora o autofondeadora (FONDEADOR o CONFIRMING habilitado,
     * mismo criterio que ms-administrator-front-neg) puede quedar APROBADA sin
     * tener aún verificadas sus políticas de pago de tesorería ni sus cuentas
     * bancarias (ver ms-administrator-front-neg CompaniesPendingComponent): esas
     * dos secciones se difieren para que la empresa pueda entrar y cargarlas, en
     * vez de bloquear su aprobación por completo. Mientras no las cargue y el
     * administrador las verifique, no puede operar ninguna negociación.
     */
    private void validarTesoreriaVerificadaSiCalifica(Company company, String rutEmpresa) {
        boolean calificaParaTesoreria = companyOperationPort.isEnabled(company.getIdeCompany(), "FONDEADOR")
                || companyOperationPort.isEnabled(company.getIdeCompany(), "CONFIRMING");

        if (calificaParaTesoreria && !companySectionVerificationPort.tesoreriaYCuentasVerificadas(company.getIdeCompany())) {
            throw new AccionNoPermitidaException(
                    "La empresa con NIT " + rutEmpresa + " todavía no tiene verificadas sus políticas de pago "
                            + "de tesorería y cuentas bancarias. Debe configurarlas y esperar la verificación "
                            + "del administrador de la plataforma antes de poder operar.",
                    "TESORERIA_NO_VERIFICADA");
        }
    }

    /**
     * Motivo de rechazo de una factura apto para el cliente: el mensaje de las excepciones del dominio
     * ({@link NegociadorException}) está escrito para él; cualquier otra recibe un mensaje fijo.
     */
    /**
     * Fase 28d (A2): UNA transacción Mongo con el marcador CREADA (índice único por {@code _id}), la negociación con el id
     * generado antes de los cupos, las facturas NEGOCIADA con su {@code negociacionId} y el correo de creación (outbox
     * Mongo, id determinista). Si el reconciliador ya puso CANCELADA (la creación tardó más que la gracia), el marcador
     * choca, la transacción se aborta y el cliente recibe un 409 claro: sus cupos se devuelven (o ya se devolvieron).
     * Un fallo técnico de Mongo deja la creación al reconciliador (CUPOS_DESCONTADOS), que completa o revierte exacto.
     */
    private Negociacion guardarEnMongo(NegociacionRequestDto request, Negociacion negociacion,
            List<FacturaNegociada> facturasAceptadas) {
        String id = negociacion.getId();
        // Fase 28d (A5b): con reintento ante un conflicto transitorio (otra creación escribiendo la misma factura): al
        // reintentar, el compare-and-set de marcarComoNegociada decide y la perdedora recibe FACTURA_CON_NEGOCIACION_VIVA.
        return transaccionMongo.ejecutarConReintento(() -> {
            if (!marca.crear(id, MarcaNegociacionOutputPort.Tipo.CREADA)) {
                log.warning(String.format("[NEGOCIACION-CANCELADA] %s: la creación llegó después de la cancelación del "
                        + "reconciliador; no se crea", id));
                throw new ConflictoException("La negociación tardó demasiado en completarse y se canceló; los cupos "
                        + "reservados se restablecen. Vuelve a intentarlo.", NEGOCIACION_CANCELADA);
            }
            Negociacion guardada = negociacionPort.save(negociacion);
            for (FacturaNegociada fn : facturasAceptadas) {
                procesarFacturaNegociacionService.marcarComoNegociada(request, fn);
                facturaPatchPort.actualizarNegociacionIdPorCufe(ResolutorFactura.referencia(fn), id);
            }
            notificacionService.notificarCreacionNegociacion(guardada);
            return guardada;
        });
    }

    /**
     * Una factura de la solicitud: aceptada (sus cupos descontados), rechazada por negocio (como siempre: queda fuera con
     * su motivo y las demás se negocian) o, por un fallo técnico, aborta toda la creación (Fase 28d, A2, decisión de John):
     * se devuelve exacto lo ya descontado en las facturas anteriores, con la plantilla A, y la excepción sube.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private void procesarUna(NegociacionRequestDto request, FacturaNegociada facturaReq, String idNegociacion,
            List<FacturaNegociada> facturasAceptadas, List<String> errores, List<String> facturasRechazadas,
            List<RechazoFacturaDto> rechazos, ContextoCupoIndirecto contextoIndirecto) {
        try {
            facturasAceptadas.add(procesarFacturaNegociacionService.procesarFactura(request, facturaReq, idNegociacion,
                    contextoIndirecto));
        } catch (ServicioDependienteNoDisponibleException e) {
            abortar(idNegociacion, facturasAceptadas, e);
            throw e;
        } catch (NegociadorException e) {
            if (CODIGOS_CONCURRENCIA_CUPO.contains(e.getCodigo())) {
                abortarPorConcurrencia(idNegociacion, facturaReq, facturasAceptadas);
            }
            log.info(String.format("Factura %s no negociada: %s", facturaReq.getNumeroFactura(), e.getMessage()));
            String motivo = motivoRechazo(e);
            errores.add(motivo);
            facturasRechazadas.add(facturaReq.getNumeroFactura() + " -> " + motivo);
            DetalleCupoIndirectoDto detalle = detalleCupoIndirecto(e);
            rechazos.add(RechazoFacturaDto.builder().numeroFactura(facturaReq.getNumeroFactura()).codigo(e.getCodigo())
                    .mensaje(motivo).cupoIndirecto(detalle).build());
            if (detalle != null) {
                avisoCupoIndirecto.avisar(facturaReq.getRutProveedor(), detalle);
            }
        } catch (RuntimeException e) {
            log.log(java.util.logging.Level.SEVERE, String.format("Error técnico procesando la factura %s: se aborta "
                    + "la creación de la negociación %s", facturaReq.getNumeroFactura(), idNegociacion), e);
            abortar(idNegociacion, facturasAceptadas, e);
            throw e;
        }
    }

    /**
     * Fase 28d (A2): aborta la creación por un fallo técnico. Pone primero el marcador CANCELADA (si la negociación ya
     * existiera en Mongo, no revierte: el reconciliador completa) y devuelve exacto lo descontado en cada factura ya
     * procesada, con la plantilla A. Si la reversión falla aquí, las operaciones siguen en CUPOS_DESCONTADOS y las
     * termina el reconciliador.
     */
    private void abortar(String idNegociacion, List<FacturaNegociada> procesadas, RuntimeException causa) {
        if (procesadas.isEmpty()) {
            return;
        }
        boolean cancelada = marca.crear(idNegociacion, MarcaNegociacionOutputPort.Tipo.CANCELADA)
                || marca.buscar(idNegociacion).filter(t -> t == MarcaNegociacionOutputPort.Tipo.CANCELADA).isPresent();
        if (!cancelada) {
            log.warning(String.format("[NEGOCIACION-ABORTADA] %s: ya está en Mongo; la completa el reconciliador",
                    idNegociacion));
            return;
        }
        for (FacturaNegociada fn : procesadas) {
            try {
                reversionExacta.revertir(TipoOperacionPendiente.CREAR_NEGOCIACION, idNegociacion, fn.getCufe(),
                        "creación abortada por un error técnico (" + causa.getClass().getSimpleName() + ")");
            } catch (RuntimeException e) {
                log.log(java.util.logging.Level.SEVERE, String.format("[NEGOCIACION-ABORTADA] %s factura %s: la "
                        + "reversión inmediata falló; la termina el reconciliador", idNegociacion, fn.getNumeroFactura()), e);
            }
        }
    }

    /**
     * Fase 28d (A5b, ajuste de John): otra operación modificó a la vez el cupo de la empresa o del fondeador (bloqueo
     * optimista). Se vuelve a mirar la factura: si ya tiene una negociación viva (la otra creación del mismo CUFE), 409 con
     * FACTURA_CON_NEGOCIACION_VIVA; si no, 409 con CUPO_CONCURRENCIA_REINTENTAR. En los dos casos se aborta la creación y
     * se devuelve exacto lo ya descontado de las facturas anteriores de esta solicitud, sin la plantilla A.
     */
    private void abortarPorConcurrencia(String idNegociacion, FacturaNegociada facturaReq,
            List<FacturaNegociada> procesadas) {
        boolean viva = negociacionVivaDe(facturaReq.getCufe());
        rechazarPorNegociacionViva(idNegociacion, procesadas);
        if (viva) {
            throw new ConflictoException(ProcesarFacturaNegociacionServiceImpl.FACTURA_CON_NEGOCIACION_VIVA
                    + ": la factura " + facturaReq.getNumeroFactura() + " ya tiene una negociación en curso; no se creó "
                    + "la negociación.", ProcesarFacturaNegociacionServiceImpl.FACTURA_CON_NEGOCIACION_VIVA);
        }
        throw new ConflictoException(CUPO_CONCURRENCIA_REINTENTAR + ": otra operación modificó el cupo al mismo tiempo; "
                + "no se creó la negociación. Vuelve a intentarlo.", CUPO_CONCURRENCIA_REINTENTAR);
    }

    /**
     * Si la factura ya tiene una negociación viva: la factura NEGOCIADA o ENDOSADA, o una creación viva registrada para su
     * CUFE. Se reintenta unas veces con una espera corta: la otra creación puede estar confirmando su transacción.
     */
    private boolean negociacionVivaDe(String cufe) {
        if (cufe == null) {
            return false;
        }
        for (int intento = 0; intento < INTENTOS_VERIFICACION_VIVA; intento++) {
            boolean estadoVivo = facturaPort.findByCufe(cufe)
                    .map(f -> "NEGOCIADA".equals(f.getEstadoNegociacion()) || "ENDOSADA".equals(f.getEstadoNegociacion()))
                    .orElse(false);
            if (estadoVivo || operaciones.existeCreacionViva(cufe)) {
                return true;
            }
            try {
                Thread.sleep(ESPERA_VERIFICACION_VIVA_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Fase 28d (A5b): la transacción Mongo se deshizo porque una factura ya tenía otra negociación viva. Se marca la
     * creación CANCELADA (el reconciliador no la completa) y se devuelve exacto lo descontado de cada factura, SIN la
     * plantilla A: el cliente recibe el error FACTURA_CON_NEGOCIACION_VIVA en la misma respuesta. Si la reversión falla
     * aquí, la termina el reconciliador (CUPOS_DESCONTADOS con el marcador CANCELADA).
     */
    private void rechazarPorNegociacionViva(String idNegociacion, List<FacturaNegociada> procesadas) {
        log.warning(String.format("[NEGOCIACION-FACTURA-VIVA] %s: una factura ya tenía otra negociación viva; se cancela "
                + "la creación y se devuelven los cupos", idNegociacion));
        boolean cancelada = marca.crear(idNegociacion, MarcaNegociacionOutputPort.Tipo.CANCELADA)
                || marca.buscar(idNegociacion).filter(t -> t == MarcaNegociacionOutputPort.Tipo.CANCELADA).isPresent();
        if (!cancelada) {
            return;
        }
        for (FacturaNegociada fn : procesadas) {
            try {
                reversionExacta.revertir(TipoOperacionPendiente.NEGOCIACION_VIVA, idNegociacion, fn.getCufe(),
                        "la factura ya tenía otra negociación viva");
            } catch (RuntimeException e) {
                log.log(java.util.logging.Level.SEVERE, String.format("[NEGOCIACION-FACTURA-VIVA] %s factura %s: la "
                        + "reversión inmediata falló; la termina el reconciliador", idNegociacion, fn.getNumeroFactura()), e);
            }
        }
    }

    /** Fase 28d: los montos del rechazo por cupo indirecto, o {@code null} si el rechazo es por otro motivo. */
    private static DetalleCupoIndirectoDto detalleCupoIndirecto(NegociadorException e) {
        if (e instanceof CupoIndirectoInsuficienteException sinCupo) {
            return sinCupo.getDetalle();
        }
        return null;
    }

    private static String motivoRechazo(Exception e) {
        if (e instanceof NegociadorException) {
            return e.getMessage();
        }
        return MENSAJE_ERROR_FACTURA;
    }
}
