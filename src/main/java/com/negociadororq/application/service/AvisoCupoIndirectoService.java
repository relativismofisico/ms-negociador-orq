package com.negociadororq.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.negociadororq.application.dto.DetalleCupoIndirectoDto;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort;
import com.negociadororq.application.util.Pesos;
import com.negociadororq.domain.enums.TipoNotificacion;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.model.Company;

/**
 * Fase 28d (punto 1.2c, decisión de John): aviso al FONDEADOR cuando una factura de un proveedor no cabe en el cupo
 * indirecto de su oferta (él es quien edita la oferta y recarga el cupo; la pagadora no puede hacer nada).
 *
 * <p>Por el outbox JDBC ({@link CorreoTransaccionalOutputPort}) con id de evento determinista
 * {@code SHA-256(CUPO_INDIRECTO_INSUFICIENTE|fondeador|pagador|proveedor|fecha de Colombia)}: como máximo un correo por
 * trío al día (el outbox no escribe un id repetido y notificaciones deduplica por id). ms-notificaciones-util resuelve
 * los usuarios activos del fondeador (o el correo de la empresa) y rellena {@code {correoSoporte}}.</p>
 *
 * <p>Se registra FUERA de la transacción de la factura (que se deshace al rechazarla). Un fallo al registrar el aviso
 * no cambia la respuesta de la negociación: queda la alerta {@code [AVISO-CUPO-INDIRECTO-FALLIDO]}.</p>
 */
public class AvisoCupoIndirectoService {

    private static final Logger LOG = Logger.getLogger(AvisoCupoIndirectoService.class.getName());
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private final CorreoTransaccionalOutputPort correos;
    private final CompanyOutputPort companyPort;
    private final ClockPort clock;

    /**
     * @param correos     outbox JDBC
     * @param companyPort nombre del proveedor
     * @param clock       reloj (la fecha de Colombia del id)
     */
    public AvisoCupoIndirectoService(final CorreoTransaccionalOutputPort correos, final CompanyOutputPort companyPort,
            final ClockPort clock) {
        this.correos = correos;
        this.companyPort = companyPort;
        this.clock = clock;
    }

    /**
     * @param rutProveedor proveedor que intentó negociar
     * @param detalle      montos y nombres del rechazo
     */
    public void avisar(final String rutProveedor, final DetalleCupoIndirectoDto detalle) {
        try {
            final LocalDate hoy = clock.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(COLOMBIA).toLocalDate();
            final String id = idEvento(detalle.getRutFondeador(), detalle.getRutPagador(), rutProveedor, hoy);
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("nombreFondeador", detalle.getFondeador());
            data.put("proveedor", companyPort.findByNit(rutProveedor).map(Company::getNameCompany).orElse(rutProveedor));
            data.put("pagador", detalle.getPagador());
            data.put("requerido", Pesos.formato(detalle.getRequerido()));
            data.put("disponible", Pesos.formato(detalle.getDisponible()));
            data.put("faltante", Pesos.formato(detalle.getFaltante()));
            final NotificacionEmailEvent evento = NotificacionEmailEvent.builder()
                    .idEvento(id)
                    .tipoNotificacion(TipoNotificacion.CUPO_INDIRECTO_INSUFICIENTE.name())
                    .asunto(TipoNotificacion.CUPO_INDIRECTO_INSUFICIENTE.name())
                    .destinatarios(List.of(new DestinatarioEmail("FONDEADOR", detalle.getRutFondeador(), "EMPRESA")))
                    .data(data)
                    .fecha(clock.now())
                    .build();
            if (!correos.registrar(id, evento)) {
                LOG.info(() -> "[AVISO-CUPO-INDIRECTO] fondeador " + detalle.getRutFondeador() + ", pagador "
                        + detalle.getRutPagador() + ", proveedor " + rutProveedor + ": ya se avisó hoy, no se envía otro");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, e, () -> "[AVISO-CUPO-INDIRECTO-FALLIDO] fondeador " + detalle.getRutFondeador()
                    + ", proveedor " + rutProveedor + ": no se pudo registrar el aviso de falta de cupo");
        }
    }

    /**
     * @param rutFondeador fondeador
     * @param rutPagador   pagador
     * @param rutProveedor proveedor
     * @param dia          fecha de Colombia
     * @return el id determinista del aviso (64 caracteres)
     */
    static String idEvento(final String rutFondeador, final String rutPagador, final String rutProveedor,
            final LocalDate dia) {
        try {
            final byte[] h = MessageDigest.getInstance("SHA-256").digest((TipoNotificacion.CUPO_INDIRECTO_INSUFICIENTE
                    + "|" + rutFondeador + "|" + rutPagador + "|" + rutProveedor + "|" + dia)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
