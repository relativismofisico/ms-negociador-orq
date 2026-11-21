package com.negociadororq.application.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.domain.enums.EstadoFactura;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Job nocturno de vencimiento (Fase 28d, A5; tabla de estados aprobada por John).
 *
 * <p>Una factura cuyo vencimiento está a {@value #DIAS_LIMITE_VENCIMIENTO} días o menos se marca VENCIDA solo si aún
 * está antes de la liquidación:</p>
 * <ul>
 *   <li>filas 1 a 4 (negociación en curso con firmas pendientes: NEGOCIADA o ENDOSADA, o SIN_NEGOCIAR con negociación y
 *       firma EN_REVISION/EN_APROBACION): VENCIDA y reversión exacta de los cupos, una sola vez por negociacion|cufe,
 *       con la plantilla A (el vencimiento no tiene otro correo);</li>
 *   <li>fila 5 (CARGADA o SIN_NEGOCIAR sin negociación viva): VENCIDA, sin cupos que devolver;</li>
 *   <li>no toca (ni revierte ni cambia el estado): firma final (CESION, DESCUENTO, APROBACION), firma RECHAZADA (A4
 *       cancelada), seguimiento de liquidación registrado, ni ningún estado terminal (LIQUIDADA, RECHAZADA, VENCIDA,
 *       BLOQUEADA, VENDIDA, INEXISTENTE; la consulta ya no los trae).</li>
 * </ul>
 *
 * <p>El paso a VENCIDA es un compare-and-set sobre el mismo documento que la firma final de la aprobación (que exige
 * "no VENCIDA"): gana exactamente uno. Si gana el job, la factura queda con {@code vencimientoReversionPendiente} hasta
 * que la reversión termina; si la corrida se cae en medio, la siguiente la retoma. La reversión es idempotente.</p>
 */
public class FacturaVencimientoService {

    /** Días hasta el vencimiento a partir de los cuales la factura vence. */
    public static final long DIAS_LIMITE_VENCIMIENTO = 10;
    /** Estados que trae la consulta (los demás son terminales o no son de este job). */
    public static final List<String> ESTADOS_CANDIDATOS = List.of(EstadoFactura.CARGADA.name(),
            EstadoFactura.SIN_NEGOCIAR.name(), EstadoFactura.NEGOCIADA.name(), "ENDOSADA");

    private static final Logger LOG = Logger.getLogger(FacturaVencimientoService.class.getName());
    private static final Set<String> FIRMAS_PROTEGIDAS = Set.of(EstadoFirma.CESION.name(),
            EstadoFirma.DESCUENTO.name(), EstadoFirma.APROBACION.name(), EstadoFirma.RECHAZADA.name());
    private static final Set<String> FIRMAS_EN_CURSO = Set.of(EstadoFirma.EN_REVISION.name(),
            EstadoFirma.EN_APROBACION.name());

    /** Qué hace el job con una factura (filas de la tabla de A5). */
    enum Decision {
        /** Filas 1 a 4: VENCIDA y reversión exacta. */
        VENCER_Y_REVERTIR,
        /** Fila 5: VENCIDA sin cupos que devolver. */
        VENCER_SIN_REVERTIR,
        /** Filas 6 a 11: no se toca. */
        NO_TOCAR
    }

    /**
     * Resultado de una corrida.
     *
     * @param vencidas    marcadas VENCIDA en esta corrida
     * @param revertidas  reversiones que terminaron (nuevas o retomadas)
     * @param protegidas  candidatas que no se tocaron (firma final, seguimiento, rechazada)
     * @param perdidas    compare-and-set perdido: otro proceso cambió la factura (se reevalúa la próxima noche)
     * @param fallidas    errores; la reversión pendiente se retoma en la próxima corrida
     */
    public record Resumen(int vencidas, int revertidas, int protegidas, int perdidas, int fallidas) {
    }

    /** Contadores de una corrida. */
    private static final class Contadores {
        private int vencidas;
        private int revertidas;
        private int protegidas;
        private int perdidas;
        private int fallidas;
    }

    private final FacturaOutputPort facturaPort;
    private final FacturaPatchOutputPort facturaPatch;
    private final OperacionPendienteOutputPort operaciones;
    private final ReversionExactaService reversionExacta;
    private final ClockPort clock;

    /**
     * @param facturaPort     lectura de facturas
     * @param facturaPatch    compare-and-set del estado
     * @param operaciones     operacion_pendiente (seguimiento de liquidación)
     * @param reversionExacta reversión exacta de lo descontado al crear la negociación
     * @param clock           reloj
     */
    public FacturaVencimientoService(final FacturaOutputPort facturaPort, final FacturaPatchOutputPort facturaPatch,
            final OperacionPendienteOutputPort operaciones, final ReversionExactaService reversionExacta,
            final ClockPort clock) {
        this.facturaPort = facturaPort;
        this.facturaPatch = facturaPatch;
        this.operaciones = operaciones;
        this.reversionExacta = reversionExacta;
        this.clock = clock;
    }

    /**
     * Una corrida: primero retoma las reversiones que quedaron pendientes, después evalúa las candidatas.
     *
     * @return el resumen de la corrida
     */
    public Resumen actualizarFacturasVencidas() {
        LOG.info("Iniciando validacion nocturna de vencimientos");
        Contadores c = new Contadores();

        for (Factura pendiente : facturaPort.findVencidasConReversionPendiente()) {
            LOG.info(String.format("[VENCIMIENTO-REANUDADO] factura %s: se retoma la reversión de cupos",
                    pendiente.getCufe()));
            revertir(pendiente, c);
        }

        LocalDate hoy = clock.now().toLocalDate();
        List<Factura> candidatas = facturaPort.findCandidatasVencimiento(hoy.plusDays(DIAS_LIMITE_VENCIMIENTO),
                ESTADOS_CANDIDATOS);
        for (Factura factura : candidatas) {
            try {
                evaluar(factura, hoy, c);
            } catch (RuntimeException ex) {
                c.fallidas++;
                LOG.log(Level.SEVERE, String.format("Error validando factura %s", factura.getCufe()), ex);
            }
        }

        Resumen r = new Resumen(c.vencidas, c.revertidas, c.protegidas, c.perdidas, c.fallidas);
        LOG.info(String.format("Proceso finalizado. Facturas vencidas actualizadas=%d revertidas=%d protegidas=%d "
                + "perdidas=%d fallidas=%d", r.vencidas(), r.revertidas(), r.protegidas(), r.perdidas(), r.fallidas()));
        return r;
    }

    private void evaluar(final Factura factura, final LocalDate hoy, final Contadores c) {
        if (factura.getFechavencimientoFactura() == null
                || ChronoUnit.DAYS.between(hoy, factura.getFechavencimientoFactura()) > DIAS_LIMITE_VENCIMIENTO) {
            return;
        }
        Decision decision = decidir(factura);
        if (decision != Decision.NO_TOCAR && tieneSeguimientoDeLiquidacion(factura)) {
            decision = Decision.NO_TOCAR;
        }
        if (decision == Decision.NO_TOCAR) {
            c.protegidas++;
            LOG.info(String.format("[VENCIMIENTO-NO-TOCA] factura %s estado=%s firma=%s", factura.getCufe(),
                    factura.getEstadoNegociacion(), factura.getEstadoFirma()));
            return;
        }
        boolean conReversion = decision == Decision.VENCER_Y_REVERTIR;
        if (!facturaPatch.marcarVencidaSiCoincide(factura.getId(), factura.getEstadoNegociacion(),
                factura.getEstadoFirma(), conReversion)) {
            c.perdidas++;
            LOG.info(String.format("[VENCIMIENTO-CAS-PERDIDO] factura %s: cambió desde la lectura; no se toca",
                    factura.getCufe()));
            return;
        }
        c.vencidas++;
        LOG.info(String.format("Factura %s marcada como VENCIDA", factura.getCufe()));
        if (conReversion) {
            revertir(factura, c);
        }
    }

    /**
     * Fila de la tabla de A5 para la factura leída. El seguimiento de liquidación (MySQL) se mira aparte.
     *
     * @param f factura
     * @return la decisión
     */
    static Decision decidir(final Factura f) {
        String estado = f.getEstadoNegociacion();
        String firma = f.getEstadoFirma();
        if (firma != null && FIRMAS_PROTEGIDAS.contains(firma)) {
            return Decision.NO_TOCAR;
        }
        boolean conNegociacion = f.getNegociacionId() != null;
        if (EstadoFactura.NEGOCIADA.name().equals(estado) || "ENDOSADA".equals(estado)) {
            return revertirSi(conNegociacion);
        }
        if (EstadoFactura.SIN_NEGOCIAR.name().equals(estado)) {
            return revertirSi(conNegociacion && FIRMAS_EN_CURSO.contains(firma));
        }
        if (EstadoFactura.CARGADA.name().equals(estado)) {
            return Decision.VENCER_SIN_REVERTIR;
        }
        return Decision.NO_TOCAR;
    }

    private static Decision revertirSi(final boolean hayCuposQueDevolver) {
        if (hayCuposQueDevolver) {
            return Decision.VENCER_Y_REVERTIR;
        }
        return Decision.VENCER_SIN_REVERTIR;
    }

    private boolean tieneSeguimientoDeLiquidacion(final Factura f) {
        if (f.getNegociacionId() == null || f.getCufe() == null) {
            return false;
        }
        return operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE,
                OperacionPendiente.clave(f.getNegociacionId(), f.getCufe())).isPresent();
    }

    private void revertir(final Factura factura, final Contadores c) {
        if (factura.getNegociacionId() == null || factura.getCufe() == null) {
            facturaPatch.quitarReversionPendiente(factura.getId());
            return;
        }
        try {
            ReversionExactaService.Resultado r = reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO,
                    factura.getNegociacionId(), factura.getCufe(), "vencimiento de la factura");
            facturaPatch.quitarReversionPendiente(factura.getId());
            c.revertidas++;
            LOG.info(String.format("[VENCIMIENTO-REVERSION] factura %s: %s", factura.getCufe(), r));
        } catch (RuntimeException ex) {
            c.fallidas++;
            LOG.log(Level.SEVERE, String.format("[VENCIMIENTO-REVERSION-PENDIENTE] factura %s: se retoma en la próxima "
                    + "corrida", factura.getCufe()), ex);
        }
    }
}
