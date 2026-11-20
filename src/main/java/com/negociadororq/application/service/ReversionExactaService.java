package com.negociadororq.application.service;

import java.util.Optional;
import java.util.logging.Logger;

import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.domain.model.DescuentoCupos;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Fase 28d (A2): devuelve exactamente lo que la creación de la negociación descontó para una factura
 * ({@link DescuentoCupos}, guardado en la operación CREAR_NEGOCIACION {@code negociacion|cufe}), una sola vez, venga
 * del camino que venga (liquidación fallida, rechazo por OTP, endoso rechazado por vigencia, vencimiento).
 *
 * <ul>
 *   <li>UNA transacción MySQL: el CAS de la operación (COMPLETADA o CUPOS_DESCONTADOS → REVERTIDA) y cada devolución. Si
 *       una devolución falla, nada queda confirmado (ni el CAS).</li>
 *   <li>CAS perdido: otro camino (o una entrega concurrente) ya revirtió; no se devuelve nada otra vez.</li>
 *   <li>Sin registro de lo descontado (negociaciones anteriores al cambio) o con otro formato: no se adivina. Queda una
 *       operación del camino en {@code REVISION_MANUAL} con {@code [OPERACION-PENDIENTE-ATASCADA]} y el contador.</li>
 * </ul>
 */
public class ReversionExactaService {

    /** Estado de CREAR_NEGOCIACION: cupos descontados, la negociación todavía no está en Mongo (A2, commit 2). */
    public static final String CUPOS_DESCONTADOS = "CUPOS_DESCONTADOS";
    /** Estado de CREAR_NEGOCIACION: la negociación existe. */
    public static final String COMPLETADA = "COMPLETADA";
    /** Estado de CREAR_NEGOCIACION: lo descontado se devolvió (una vez). */
    public static final String REVERTIDA = "REVERTIDA";

    private static final Logger LOG = Logger.getLogger(ReversionExactaService.class.getName());
    private static final int LARGO_MOTIVO = 200;

    /** Resultado de una reversión. */
    public enum Resultado {
        /** Esta llamada devolvió los cupos. */
        REVERTIDA,
        /** Ya estaban devueltos (otro camino u otra entrega): nada cambió. */
        YA_REVERTIDA,
        /** No hay registro de lo descontado: REVISION_MANUAL, nada cambió. */
        SIN_REGISTRO
    }

    private final OperacionPendienteOutputPort operaciones;
    private final CupoReversionService cupos;
    private final IndirectFunderOfferSupplierQuotaOutputPort indirecto;
    private final TransactionOperations transaccion;

    /**
     * @param operaciones {@code operacion_pendiente}
     * @param cupos       devoluciones de operador, empresa y fondeadores
     * @param indirecto   devolución de la oferta indirecta de FACTORING
     * @param transaccion transacción MySQL
     */
    public ReversionExactaService(final OperacionPendienteOutputPort operaciones, final CupoReversionService cupos,
            final IndirectFunderOfferSupplierQuotaOutputPort indirecto, final TransactionOperations transaccion) {
        this.operaciones = operaciones;
        this.cupos = cupos;
        this.indirecto = indirecto;
        this.transaccion = transaccion;
    }

    /**
     * @param camino        quién revierte (para la operación de revisión manual si no hay registro)
     * @param negociacionId negociación
     * @param cufe          CUFE de la factura
     * @param motivo        motivo de la reversión (sin datos sensibles)
     * @return qué pasó
     */
    public Resultado revertir(final TipoOperacionPendiente camino, final String negociacionId, final String cufe,
            final String motivo) {
        final String clave = OperacionPendiente.clave(negociacionId, cufe);
        final Optional<OperacionPendiente> registro =
                operaciones.buscar(TipoOperacionPendiente.CREAR_NEGOCIACION, clave);
        if (registro.isEmpty() || registro.get().datos() == null) {
            atascada(camino, clave, motivo, "sin registro de lo descontado");
            return Resultado.SIN_REGISTRO;
        }
        if (REVERTIDA.equals(registro.get().estado())) {
            return Resultado.YA_REVERTIDA;
        }
        final DescuentoCupos descuento;
        try {
            descuento = DescuentoCupos.deJson(registro.get().datos());
        } catch (IllegalArgumentException e) {
            atascada(camino, clave, motivo, e.getMessage());
            return Resultado.SIN_REGISTRO;
        }
        final Boolean revertida = transaccion.execute(estado -> {
            if (!operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION, clave, COMPLETADA, REVERTIDA)
                    && !operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION, clave, CUPOS_DESCONTADOS,
                            REVERTIDA)) {
                return false;
            }
            descuento.descuentos().forEach(this::devolver);
            return true;
        });
        if (Boolean.TRUE.equals(revertida)) {
            LOG.info(() -> "[REVERSION-EXACTA] " + clave + " (" + camino + "): " + descuento.descuentos().size()
                    + " cupo(s) devueltos exactamente");
            return Resultado.REVERTIDA;
        }
        LOG.info(() -> "[REVERSION-EXACTA] " + clave + " (" + camino + "): ya estaba revertida, no se devuelve otra vez");
        return Resultado.YA_REVERTIDA;
    }

    private void devolver(final DescuentoCupos.Descuento d) {
        switch (d.tipo()) {
            case OPERADOR -> cupos.revertirCupoUsuario(d.a(), d.monto());
            case EMPRESA -> cupos.revertirCupoEmpresaEnTransaccionActual(d.a(), d.monto());
            case FONDEADOR_FACTORING -> cupos.revertirCupoFondeadorFactoring(d.a(), d.b(), d.monto());
            case FONDEADOR_CONFIRMING -> cupos.revertirCupoFondeadorConfirming(d.a(), d.b(), d.monto());
            case INDIRECTO -> {
                if (indirecto.devolverCupo(d.a(), d.b(), d.monto()) != 1) {
                    throw new IllegalStateException("no existe la fila de consumo de la oferta indirecta " + d.a()
                            + " del proveedor " + d.b());
                }
            }
            default -> throw new IllegalStateException("tipo de descuento desconocido " + d.tipo());
        }
    }

    private void atascada(final TipoOperacionPendiente camino, final String clave, final String motivo,
            final String causa) {
        if (operaciones.registrar(camino, clave, ReconciliadorOperacionesService.REVISION_MANUAL,
                "{\"motivo\":\"" + texto(motivo) + "\",\"causa\":\"" + texto(causa) + "\"}")) {
            ReconciliadorOperacionesService.contarAtascada();
            LOG.severe(() -> "[OPERACION-PENDIENTE-ATASCADA] " + camino + " " + clave + " pasa a "
                    + ReconciliadorOperacionesService.REVISION_MANUAL + ": " + causa
                    + "; los cupos no se tocan hasta revisarla");
        }
    }

    /** Texto seguro dentro de un JSON simple (sin comillas ni barras) y acotado. */
    private static String texto(final String valor) {
        if (valor == null) {
            return "";
        }
        String t = valor.replaceAll("[\"\\\\\\p{Cntrl}]", " ");
        if (t.length() > LARGO_MOTIVO) {
            t = t.substring(0, LARGO_MOTIVO);
        }
        return t;
    }
}
