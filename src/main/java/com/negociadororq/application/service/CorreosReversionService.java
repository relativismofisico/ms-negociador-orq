package com.negociadororq.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.enums.TipoNotificacion;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.DescuentoCupos;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;

/**
 * Fase 28d (A2): correos de la reversión, aprobados por John, a los operadores registrados de la empresa que inició la
 * operación y de su contraparte (ms-notificaciones-util resuelve los operadores de cada empresa y rellena
 * {@code {nombreContacto}} y {@code {correoSoporte}}).
 *
 * <ul>
 *   <li>Plantilla A ({@link TipoNotificacion#OPERACION_REVERTIDA}): la reversión devolvió exactamente lo descontado. Se
 *       registra en la MISMA transacción MySQL de la reversión.</li>
 *   <li>Plantilla B ({@link TipoNotificacion#OPERACION_EN_REVISION}, 2.8): la reversión no pudo terminar y la operación
 *       pasa a REVISION_MANUAL. Se registra en la misma transacción de ese paso. Nunca para una liquidación sin
 *       resultado (A4: no hubo reversión).</li>
 * </ul>
 *
 * <p>Por el outbox JDBC ({@link CorreoTransaccionalOutputPort}) con id de evento determinista
 * ({@code SHA-256(negociacion|cufe|plantilla)}): un reintento o un segundo camino no envía otro. Los datos no llevan el
 * CUFE ni datos internos (montos, ids de cupo).</p>
 */
public class CorreosReversionService {

    static final String PLANTILLA_A = "A";
    static final String PLANTILLA_B = "B";

    private static final Logger LOG = Logger.getLogger(CorreosReversionService.class.getName());
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "CO"));
    private static final String FACTORING = "FACTORING";

    private final CorreoTransaccionalOutputPort correos;
    private final CompanyOutputPort companyPort;
    private final NegociacionOutputPort negociacionPort;
    private final ClockPort clock;

    /**
     * @param correos         outbox JDBC (en la transacción activa)
     * @param companyPort     nombres de las empresas
     * @param negociacionPort contexto, si el registro no lo trae
     * @param clock           reloj
     */
    public CorreosReversionService(final CorreoTransaccionalOutputPort correos, final CompanyOutputPort companyPort,
            final NegociacionOutputPort negociacionPort, final ClockPort clock) {
        this.correos = correos;
        this.companyPort = companyPort;
        this.negociacionPort = negociacionPort;
        this.clock = clock;
    }

    /**
     * Plantilla A (en la transacción de la reversión).
     *
     * @param negociacionId negociación
     * @param cufe          factura
     * @param contexto      contexto del registro, o null (se toma de la negociación)
     */
    public void revertida(final String negociacionId, final String cufe, final DescuentoCupos.Contexto contexto) {
        enviar(TipoNotificacion.OPERACION_REVERTIDA, PLANTILLA_A, negociacionId, cufe, contexto);
    }

    /**
     * Plantilla B (en la transacción del paso a REVISION_MANUAL).
     *
     * @param negociacionId negociación
     * @param cufe          factura
     * @param contexto      contexto del registro, o null (se toma de la negociación)
     */
    public void enRevision(final String negociacionId, final String cufe, final DescuentoCupos.Contexto contexto) {
        enviar(TipoNotificacion.OPERACION_EN_REVISION, PLANTILLA_B, negociacionId, cufe, contexto);
    }

    /**
     * @param negociacionId negociación
     * @param cufe          factura
     * @param plantilla     A o B
     * @return el id determinista del correo (64 caracteres)
     */
    static String idEvento(final String negociacionId, final String cufe, final String plantilla) {
        try {
            final byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest((negociacionId + "|" + cufe + "|" + plantilla).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void enviar(final TipoNotificacion tipo, final String plantilla, final String negociacionId,
            final String cufe, final DescuentoCupos.Contexto contexto) {
        final Optional<DescuentoCupos.Contexto> ctx = Optional.ofNullable(contexto)
                .or(() -> desdeNegociacion(negociacionId, cufe));
        if (ctx.isEmpty()) {
            LOG.severe(() -> "[CORREO-REVERSION-SIN-CONTEXTO] negociación " + negociacionId + ": no se puede enviar la "
                    + "plantilla " + plantilla + " (sin empresas ni factura); revisar a mano");
            return;
        }
        final DescuentoCupos.Contexto c = ctx.get();
        String rolIniciadora = "PAGADOR";
        String rolContraparte = "PROVEEDOR";
        String tipoTexto = "Confirming";
        if (FACTORING.equalsIgnoreCase(c.tipoOperacion())) {
            rolIniciadora = "PROVEEDOR";
            rolContraparte = "PAGADOR";
            tipoTexto = "Factoring";
        }
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("tipoOperacion", tipoTexto);
        data.put("numeroFactura", c.numeroFactura());
        data.put("idNegociacion", negociacionId);
        data.put("fechaHora", clock.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(COLOMBIA).format(FECHA_HORA));
        data.put("rutEmpresa", c.rutEmpresa());
        data.put("nombreEmpresa", nombre(c.rutEmpresa()));
        data.put("rutContraparte", c.rutContraparte());
        data.put("nombreContraparte", nombre(c.rutContraparte()));
        final String id = idEvento(negociacionId, cufe, plantilla);
        final NotificacionEmailEvent evento = NotificacionEmailEvent.builder()
                .idEvento(id)
                .idNegociacion(negociacionId)
                .tipoNotificacion(tipo.name())
                .asunto(tipo.name())
                .destinatarios(List.of(new DestinatarioEmail(rolIniciadora, c.rutEmpresa(), "EMPRESA"),
                        new DestinatarioEmail(rolContraparte, c.rutContraparte(), "EMPRESA")))
                .data(data)
                .fecha(clock.now())
                .build();
        if (!correos.registrar(id, evento)) {
            LOG.info(() -> "[CORREO-REVERSION] negociación " + negociacionId + ": la plantilla " + plantilla
                    + " ya estaba registrada, no se envía otra");
        }
    }

    private Optional<DescuentoCupos.Contexto> desdeNegociacion(final String negociacionId, final String cufe) {
        final Optional<Negociacion> negociacion = negociacionPort.findById(negociacionId);
        if (negociacion.isEmpty() || negociacion.get().getTipoOperacion() == null) {
            return Optional.empty();
        }
        final Negociacion n = negociacion.get();
        final String tipo = n.getTipoOperacion().getTipo();
        final Optional<FacturaNegociada> factura = Optional.ofNullable(n.getFacturas()).orElse(List.of()).stream()
                .filter(f -> cufe.equals(f.getCufe())).findFirst();
        if (factura.isEmpty()) {
            return Optional.empty();
        }
        String contraparte = factura.get().getRutProveedor();
        if (FACTORING.equalsIgnoreCase(tipo)) {
            contraparte = factura.get().getRutPagador();
        }
        return Optional.of(new DescuentoCupos.Contexto(tipo, n.getRutEmpresa(), contraparte,
                factura.get().getNumeroFactura()));
    }

    private String nombre(final String nit) {
        if (nit == null) {
            return null;
        }
        return companyPort.findByNit(nit).map(Company::getNameCompany).orElse(nit);
    }
}
