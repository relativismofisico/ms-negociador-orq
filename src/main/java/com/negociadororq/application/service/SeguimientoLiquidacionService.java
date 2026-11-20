package com.negociadororq.application.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException;
import com.negociadororq.application.port.output.CancelacionLiquidacionOutputPort;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.application.port.output.OutboxOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.ReferenciaFactura;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Fase 28d (A4): seguimiento de cada solicitud de liquidación hasta su resultado, sin revertir por tiempo.
 *
 * <p>Al escribir {@code liquidacion.solicitada} se registra {@code operacion_pendiente} {@code LIQUIDACION_PENDIENTE}
 * ({@code negociacion|cufe}) en {@link #SOLICITADA}. Cada {@code intervalo} (15 min) se toma la que sigue sin resultado
 * (una toma = un intento, con concesión para que dos instancias no la tomen a la vez) y, según su número de intento:</p>
 * <ol>
 *   <li>si la factura ya está LIQUIDADA: COMPLETADA, sin revertir; si la creación ya se revirtió por otro camino (fallida
 *       consumida, rechazo): REVERTIDA, sin tocar nada más;</li>
 *   <li>intentos 1..{@code reenvios} (4): vuelve a encolar el MISMO evento del outbox (mismo payload: el liquidador lo
 *       ignora si ya lo consumió);</li>
 *   <li>los {@code maxCancelaciones} (8) siguientes: pide al liquidador "cancelar si no procesada". LIQUIDADA cierra sin
 *       revertir; FALLIDA revierte una vez por el camino de la fallida (su correo, sin plantilla A; cubre la fallida no
 *       publicada); CANCELADA revierte una vez con la plantilla A. Liquidador caído: se reintenta en el siguiente ciclo;</li>
 *   <li>agotados: REVISION_MANUAL con {@code [OPERACION-PENDIENTE-ATASCADA]} y el contador de atascadas, SIN correo; los
 *       cupos quedan retenidos hasta que una persona la resuelva.</li>
 * </ol>
 */
public class SeguimientoLiquidacionService {

    /** Estado mientras la solicitud no tiene resultado (no lo toma el reconciliador genérico). */
    public static final String SOLICITADA = "LIQ_SOLICITADA";
    static final String COMPLETADA = "COMPLETADA";
    static final String REVERTIDA = "REVERTIDA";
    static final String ACTOR = "ms-negociador-orq";

    private static final Logger LOG = Logger.getLogger(SeguimientoLiquidacionService.class.getName());
    private static final Pattern DATOS = Pattern.compile(
            "\\{\"v\":1,\"numeroFactura\":\"((?:[^\"\\\\]|\\\\.)*)\",\"idEvento\":\"((?:[^\"\\\\]|\\\\.)*)\"}");
    private static final int GRUPO_NUMERO = 1;
    private static final int GRUPO_EVENTO = 2;

    private final OperacionPendienteOutputPort operaciones;
    private final OutboxOutputPort outbox;
    private final FacturaOutputPort facturaPort;
    private final FacturaPatchOutputPort facturaPatch;
    private final CancelacionLiquidacionOutputPort cancelacion;
    private final ReversionCuposService reversionCupos;
    private final ReversionExactaService reversionExacta;
    private final ClockPort clock;
    private final Parametros parametros;

    /**
     * Tiempos y topes del seguimiento.
     *
     * @param intervalo        espera entre pasos (15 min)
     * @param reenvios         reenvíos del evento antes de pedir la cancelación (4)
     * @param maxCancelaciones intentos de cancelación antes de la revisión manual (8)
     * @param lote             operaciones por ciclo
     */
    public record Parametros(Duration intervalo, int reenvios, int maxCancelaciones, int lote) {
    }

    /**
     * Dependencias del seguimiento.
     *
     * @param operaciones     operacion_pendiente
     * @param outbox          outbox Mongo de los eventos
     * @param facturaPort     lectura de la factura
     * @param facturaPatch    estado de la firma
     * @param cancelacion     "cancelar si no procesada" en el liquidador
     * @param reversionCupos  reversión por fallida (con su correo)
     * @param reversionExacta reversión exacta (CANCELADA, con la plantilla A)
     * @param clock           reloj
     * @param parametros      tiempos y topes
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public SeguimientoLiquidacionService(final OperacionPendienteOutputPort operaciones, final OutboxOutputPort outbox,
            final FacturaOutputPort facturaPort, final FacturaPatchOutputPort facturaPatch,
            final CancelacionLiquidacionOutputPort cancelacion, final ReversionCuposService reversionCupos,
            final ReversionExactaService reversionExacta, final ClockPort clock, final Parametros parametros) {
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.facturaPort = facturaPort;
        this.facturaPatch = facturaPatch;
        this.cancelacion = cancelacion;
        this.reversionCupos = reversionCupos;
        this.reversionExacta = reversionExacta;
        this.clock = clock;
        this.parametros = parametros;
    }

    /**
     * Registra el seguimiento de una solicitud recién escrita en el outbox. Idempotente: si ya existía, no hace nada.
     *
     * @param negociacionId negociación
     * @param cufe          CUFE
     * @param numeroFactura número (para el correo de la fallida)
     * @param idEvento      id del evento {@code liquidacion.solicitada} en el outbox (para reenviarlo)
     * @return true si lo registró ahora
     */
    public boolean registrarSolicitud(final String negociacionId, final String cufe, final String numeroFactura,
            final String idEvento) {
        return operaciones.registrar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, negociacionId + "|" + cufe,
                SOLICITADA, "{\"v\":1,\"numeroFactura\":\"" + escapar(numeroFactura) + "\",\"idEvento\":\""
                        + escapar(idEvento) + "\"}");
    }

    /**
     * Un ciclo del seguimiento.
     *
     * @return operaciones atendidas en el ciclo
     */
    public int seguir() {
        final LocalDateTime ahora = clock.now();
        final List<OperacionPendiente> tomadas = operaciones.tomarParaReanudar(List.of(SOLICITADA),
                ahora.minus(parametros.intervalo()), ahora, ahora.plus(parametros.intervalo().dividedBy(2)),
                parametros.lote());
        int atendidas = 0;
        for (final OperacionPendiente op : tomadas) {
            if (op.tipo() != TipoOperacionPendiente.LIQUIDACION_PENDIENTE) {
                continue;
            }
            try {
                atender(op);
                atendidas++;
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "[LIQUIDACION-SEGUIMIENTO] " + op.claveNatural() + ": "
                        + e.getClass().getSimpleName());
                operaciones.registrarError(op.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return atendidas;
    }

    private void atender(final OperacionPendiente op) {
        final String clave = op.claveNatural();
        final int corte = clave.indexOf('|');
        final String negociacionId = clave.substring(0, corte);
        final String cufe = clave.substring(corte + 1);
        String numero = null;
        String idEvento = null;
        if (op.datos() != null) {
            final Matcher datos = DATOS.matcher(op.datos());
            if (datos.matches()) {
                numero = datos.group(GRUPO_NUMERO);
                idEvento = datos.group(GRUPO_EVENTO);
            }
        }
        final Optional<Factura> factura = facturaPort.findByCufe(cufe);

        if (factura.isPresent() && EstadoNegociacion.LIQUIDADA.name().equals(factura.get().getEstadoNegociacion())) {
            cerrar(clave, COMPLETADA, "liquidada");
            return;
        }
        if (operaciones.buscar(TipoOperacionPendiente.CREAR_NEGOCIACION, clave)
                .filter(c -> REVERTIDA.equals(c.estado())).isPresent()) {
            cerrar(clave, REVERTIDA, "los cupos ya se devolvieron por otro camino");
            return;
        }
        if (op.intentos() <= parametros.reenvios()) {
            if (idEvento != null && outbox.reencolar(idEvento)) {
                LOG.info(() -> "[LIQUIDACION-REENVIO] " + clave + ": reenvío " + op.intentos() + " de "
                        + parametros.reenvios());
            } else {
                LOG.warning(() -> "[LIQUIDACION-REENVIO] " + clave + ": el evento ya no está en el outbox (reenvío "
                        + op.intentos() + " de " + parametros.reenvios() + ")");
            }
            return;
        }
        if (op.intentos() > parametros.reenvios() + parametros.maxCancelaciones()) {
            atascar(op);
            return;
        }
        final CancelacionLiquidacionOutputPort.Resultado resultado;
        try {
            resultado = cancelacion.cancelar(negociacionId, cufe);
        } catch (ServicioDependienteNoDisponibleException e) {
            LOG.warning(() -> "[LIQUIDACION-CANCELACION-REINTENTO] " + clave + ": liquidador no disponible (intento "
                    + (op.intentos() - parametros.reenvios()) + " de " + parametros.maxCancelaciones() + ")");
            operaciones.registrarError(op.id(), "liquidador no disponible al cancelar");
            return;
        }
        final String numeroFactura = numero;
        switch (resultado) {
            case LIQUIDADA -> cerrar(clave, COMPLETADA, "el liquidador la informa LIQUIDADA");
            case FALLIDA -> {
                reversionCupos.revertirPorFactura(negociacionId, cufe, numeroFactura, ACTOR,
                        "La liquidación no pudo completarse.");
                cerrar(clave, REVERTIDA, "el liquidador la informa FALLIDA");
            }
            default -> {
                reversionExacta.revertir(TipoOperacionPendiente.LIQUIDACION_CANCELADA, negociacionId, cufe,
                        "liquidación sin resultado cancelada");
                factura.ifPresent(f -> facturaPatch.actualizarEstadoFirmaPorCufe(
                        new ReferenciaFactura(cufe, f.getRutProveedor(), f.getRutPagador()), EstadoFirma.RECHAZADA));
                cerrar(clave, REVERTIDA, "cancelada en el liquidador");
            }
        }
    }

    private void cerrar(final String clave, final String hacia, final String motivo) {
        if (operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, clave, SOLICITADA, hacia)) {
            LOG.info(() -> "[LIQUIDACION-SEGUIMIENTO] " + clave + " -> " + hacia + ": " + motivo);
        }
    }

    private void atascar(final OperacionPendiente op) {
        if (operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, op.claveNatural(), SOLICITADA,
                ReconciliadorOperacionesService.REVISION_MANUAL)) {
            ReconciliadorOperacionesService.contarAtascada();
            LOG.severe(() -> "[OPERACION-PENDIENTE-ATASCADA] LIQUIDACION_PENDIENTE " + op.claveNatural() + " pasa a "
                    + ReconciliadorOperacionesService.REVISION_MANUAL + ": sin resultado del liquidador tras "
                    + parametros.reenvios() + " reenvíos y " + parametros.maxCancelaciones() + " intentos de cancelación;"
                    + " los cupos quedan retenidos y no se envía correo. Último error: " + op.ultimoError());
        }
    }

    private static String escapar(final String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
