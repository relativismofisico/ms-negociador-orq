package com.negociadororq.application.service;

import java.util.logging.Logger;

import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.TransaccionMongoOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.ReferenciaFactura;

/**
 * Fase 28d (A5): la firma final de una factura y su solicitud de liquidación, en UNA transacción de Mongo.
 *
 * <p>La firma es un compare-and-set "no VENCIDA" (ni otro estado terminal) sobre el mismo documento que el job de
 * vencimiento marca VENCIDA con otro compare-and-set: gana exactamente uno. Si gana la firma, en la misma transacción
 * quedan el evento {@code liquidacion.solicitada} (id determinista) y la marca {@code seguimientoLiquidacionPendiente};
 * el seguimiento (MySQL) se escribe después y, si la instancia cae antes, el reconciliador lo completa. Si gana el job,
 * no se escribe nada (ni firma ni evento) y la liquidación no se pide.</p>
 */
public class FirmaFinalLiquidacion {

    private static final Logger LOG = Logger.getLogger(FirmaFinalLiquidacion.class.getName());

    private final TransaccionMongoOutputPort transaccion;
    private final FacturaPatchOutputPort facturaPatch;
    private final OutboxService outbox;

    /**
     * @param transaccion  transacción Mongo (con reintento ante conflicto transitorio)
     * @param facturaPatch compare-and-set de la firma
     * @param outbox       outbox Mongo del evento
     */
    public FirmaFinalLiquidacion(final TransaccionMongoOutputPort transaccion, final FacturaPatchOutputPort facturaPatch,
            final OutboxService outbox) {
        this.transaccion = transaccion;
        this.facturaPatch = facturaPatch;
        this.outbox = outbox;
    }

    /**
     * @param ref           factura (CUFE y RUT de la negociación)
     * @param firmaFinal    CESION (FACTORING) o DESCUENTO (CONFIRMING)
     * @param idEvento      id determinista del evento
     * @param negociacionId negociación (agregado del evento)
     * @param topic         topic de la solicitud de liquidación
     * @param evento        la solicitud
     * @return {@code true} si la firma ganó y la solicitud quedó escrita; {@code false} si la factura ya estaba VENCIDA
     *         (o en otro estado terminal)
     */
    public boolean firmarYSolicitar(final ReferenciaFactura ref, final EstadoFirma firmaFinal, final String idEvento,
            final String negociacionId, final String topic, final Object evento) {
        return transaccion.ejecutarConReintento(() -> {
            if (!facturaPatch.marcarFirmaFinalConSolicitud(ref, firmaFinal, idEvento)) {
                return false;
            }
            if (!outbox.guardarEventoConId(idEvento, negociacionId, topic, evento)) {
                LOG.info(String.format("[LIQUIDACION-YA-SOLICITADA] negociación %s CUFE %s: el evento ya estaba en el "
                        + "outbox.", negociacionId, ref.cufe()));
            }
            return true;
        });
    }
}
