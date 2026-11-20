package com.negociadororq.application.service;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.MarcaNegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.ReferenciaFactura;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Fase 28d (A2): reanuda una creación de negociación que quedó con los cupos descontados (CUPOS_DESCONTADOS) pasada la
 * gracia: la petición murió, se cortó entre el paso MySQL y el de Mongo, o el paso Mongo no confirmó.
 *
 * <ol>
 *   <li>Inserta primero el marcador CANCELADA con el id de la negociación (índice único). Si entra, ninguna petición
 *       lenta podrá ya crear la negociación (su marcador CREADA chocará y abortará): se devuelve exacto lo descontado
 *       ({@link ReversionExactaService}, con la auditoría y la plantilla A en la misma transacción).</li>
 *   <li>Si choca, mira qué marcador hay: CREADA (la negociación existe) → completa (COMPLETADA y repara el
 *       {@code negociacionId} de la factura); CANCELADA (otra factura de la misma negociación ya la canceló) → revierte
 *       esta también.</li>
 * </ol>
 */
public class ReanudadorCrearNegociacion implements ReanudadorOperacion {

    private static final Logger LOG = Logger.getLogger(ReanudadorCrearNegociacion.class.getName());

    private final MarcaNegociacionOutputPort marca;
    private final NegociacionOutputPort negociacionPort;
    private final OperacionPendienteOutputPort operaciones;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final ReversionExactaService reversionExacta;

    /**
     * @param marca           marcador único por negociación
     * @param negociacionPort negociación (para completar)
     * @param operaciones     operaciones pendientes
     * @param facturaPatchPort reparación del {@code negociacionId} de la factura
     * @param reversionExacta devolución exacta
     */
    public ReanudadorCrearNegociacion(final MarcaNegociacionOutputPort marca, final NegociacionOutputPort negociacionPort,
            final OperacionPendienteOutputPort operaciones, final FacturaPatchOutputPort facturaPatchPort,
            final ReversionExactaService reversionExacta) {
        this.marca = marca;
        this.negociacionPort = negociacionPort;
        this.operaciones = operaciones;
        this.facturaPatchPort = facturaPatchPort;
        this.reversionExacta = reversionExacta;
    }

    @Override
    public TipoOperacionPendiente tipo() {
        return TipoOperacionPendiente.CREAR_NEGOCIACION;
    }

    @Override
    public List<String> estadosReanudables() {
        return List.of(ReversionExactaService.CUPOS_DESCONTADOS);
    }

    @Override
    public void reanudar(final OperacionPendiente operacion) {
        final String clave = operacion.claveNatural();
        final int separador = clave.indexOf(OperacionPendiente.SEPARADOR);
        final String negociacionId = clave.substring(0, separador);
        final String cufe = clave.substring(separador + 1);

        if (marca.crear(negociacionId, MarcaNegociacionOutputPort.Tipo.CANCELADA)) {
            LOG.warning(() -> "[CREACION-CANCELADA] " + clave + ": la negociación no llegó a Mongo; se cancela y se "
                    + "devuelve exacto lo descontado");
            reversionExacta.revertir(TipoOperacionPendiente.CREAR_NEGOCIACION, negociacionId, cufe,
                    "la creación de la negociación no se completó");
            return;
        }
        final Optional<MarcaNegociacionOutputPort.Tipo> existente = marca.buscar(negociacionId);
        if (existente.isPresent() && existente.get() == MarcaNegociacionOutputPort.Tipo.CANCELADA) {
            reversionExacta.revertir(TipoOperacionPendiente.CREAR_NEGOCIACION, negociacionId, cufe,
                    "la creación de la negociación no se completó");
            return;
        }
        completar(negociacionId, cufe, clave);
    }

    private void completar(final String negociacionId, final String cufe, final String clave) {
        final Negociacion negociacion = negociacionPort.findById(negociacionId).orElseThrow(() ->
                new IllegalStateException("marcador CREADA sin negociación " + negociacionId));
        final Optional<FacturaNegociada> factura = Optional.ofNullable(negociacion.getFacturas()).orElse(List.of())
                .stream().filter(f -> cufe.equals(f.getCufe())).findFirst();
        if (factura.isPresent()) {
            final FacturaNegociada f = factura.get();
            facturaPatchPort.actualizarNegociacionIdPorCufe(new ReferenciaFactura(f.getCufe(), f.getRutProveedor(),
                    f.getRutPagador()), negociacionId);
        }
        if (operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION, clave,
                ReversionExactaService.CUPOS_DESCONTADOS, ReversionExactaService.COMPLETADA)) {
            LOG.info(() -> "[CREACION-COMPLETADA] " + clave + ": la negociación ya estaba en Mongo; se completa");
        }
    }
}
