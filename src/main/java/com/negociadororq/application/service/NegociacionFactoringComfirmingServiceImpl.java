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

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;
import com.negociadororq.application.exception.AccionNoPermitidaException;
import com.negociadororq.application.exception.NegociadorException;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOperationOutputPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link NegociacionFactoringComfirmingService}.
 */
@Log
@RequiredArgsConstructor
public class NegociacionFactoringComfirmingServiceImpl implements NegociacionFactoringComfirmingService {

    /** Motivo de rechazo de una factura cuando el error no es del dominio: el detalle queda en el log. */
    static final String MENSAJE_ERROR_FACTURA = "No se pudo procesar la factura por un error interno.";

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
    @Transactional
    public NegociacionResponseDto crearNegociacion(NegociacionRequestDto request) {


        List<String> errores = new ArrayList<>();
        List<FacturaNegociada> facturasAceptadas = new ArrayList<>();
        List<String> facturasRechazadas = new ArrayList<>();

        userPort.findByUsername(request.getRutOperador())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe operador con username " + request.getRutOperador(), "OPERADOR_NO_ENCONTRADO"));

        validarOperacionHabilitada(request);
        validarUnicaContraparte(request);

        /* ======================================================
           1. Agrupar facturas por pagador SOLO en CONFIRMING
        ====================================================== */
        Map<String, List<FacturaNegociada>> facturasPorPagador = new HashMap<>();

        for (FacturaNegociada fn : request.getFacturas()) {

            Factura factura = facturaPort.findByNumeroFactura(fn.getNumeroFactura())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Factura no encontrada: " + fn.getNumeroFactura(), "FACTURA_NO_ENCONTRADA"));

            facturasPorPagador
                    .computeIfAbsent(factura.getRutPagador(), k -> new ArrayList<>())
                    .add(fn);
        }

        for (FacturaNegociada facturaReq : request.getFacturas()) {

            try {
                FacturaNegociada procesada = procesarFacturaNegociacionService.procesarFactura(request, facturaReq);
                facturasAceptadas.add(procesada);

            } catch (Exception e) {
                log.log(java.util.logging.Level.SEVERE, String.format("Error procesando factura %s", facturaReq.getNumeroFactura()), e);

                // El motivo viaja en la respuesta (errores) y queda guardado en la negociación: solo el
                // mensaje de una excepción del dominio; el de cualquier otra (BD, red, librerías) va al log.
                String motivo = motivoRechazo(e);
                errores.add(motivo);
                facturasRechazadas.add(facturaReq.getNumeroFactura() + " -> " + motivo);
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
        negociacion = negociacionPort.save(negociacion);

        // El id de la negociación solo existe tras guardarla; se deja registrado en cada
        // factura aceptada para poder sub-agrupar facturas liquidadas por negociación.
        String idNegociacionGuardada = negociacion.getId();
        facturasAceptadas.forEach(fn ->
                facturaPatchPort.actualizarNegociacionId(fn.getNumeroFactura(), idNegociacionGuardada));

        notificacionService.notificarCreacionNegociacion(negociacion);
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

        return response;
    }

    /**
     * Rechaza toda la solicitud (no factura a factura) si la empresa solicitante, o cualquiera de
     * las contrapartes involucradas (pagador en FACTORING, proveedor en CONFIRMING), no tiene
     * habilitada la operación (FACTORING/CONFIRMING) que se está intentando ejecutar. A diferencia
     * de validarPermisoOperacion (dentro de ProcesarFacturaNegociacionServiceImpl, que valida
     * factura a factura y cuyas excepciones se absorben como rechazo individual), esta se lanza
     * antes del loop para que el fallo sea un error HTTP real, no una factura rechazada silenciosa.
     */
    private void validarOperacionHabilitada(NegociacionRequestDto request) {
        String tipoOperacion = null;
        if (request.getTipoOperacion() != null) {
            tipoOperacion = request.getTipoOperacion().getTipo();
        }
        if (tipoOperacion == null) {
            return;
        }

        String tipoOperacionUpper = tipoOperacion.toUpperCase(Locale.ROOT);

        validarEmpresaHabilitada(request.getRutEmpresa(), tipoOperacionUpper);

        boolean esFactoring = "FACTORING".equals(tipoOperacionUpper);
        Set<String> contrapartes = request.getFacturas().stream()
                .map(fn -> {
                    if (esFactoring) {
                        return fn.getRutPagador();
                    }
                    return fn.getRutProveedor();
                })
                .filter(rut -> rut != null && !rut.isBlank())
                .collect(Collectors.toSet());

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
    private void validarUnicaContraparte(NegociacionRequestDto request) {
        String tipoOperacion = null;
        if (request.getTipoOperacion() != null) {
            tipoOperacion = request.getTipoOperacion().getTipo();
        }
        if (tipoOperacion == null) {
            return;
        }

        boolean esFactoring = "FACTORING".equalsIgnoreCase(tipoOperacion);
        Set<String> contrapartes = request.getFacturas().stream()
                .map(fn -> {
                    if (esFactoring) {
                        return fn.getRutPagador();
                    }
                    return fn.getRutProveedor();
                })
                .filter(rut -> rut != null && !rut.isBlank())
                .collect(Collectors.toSet());

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
    private static String motivoRechazo(Exception e) {
        if (e instanceof NegociadorException) {
            return e.getMessage();
        }
        return MENSAJE_ERROR_FACTURA;
    }
}
