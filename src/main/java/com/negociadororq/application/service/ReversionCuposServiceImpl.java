package com.negociadororq.application.service;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Implementación de {@link ReversionCuposService} (liquidación fallida).
 *
 * <ol>
 *   <li>Guarda de estado: una factura LIQUIDADA o con la firma ya RECHAZADA no se toca (ni cupos, ni firma, ni
 *       notificación).</li>
 *   <li>Fase 28d (A2): la devolución es la exacta de lo que la creación descontó ({@link ReversionExactaService}: al
 *       operador y a la empresa que negociaron, por el monto descontado, incluido el cupo indirecto de FACTORING), una sola
 *       vez por {@code negociacion|cufe} venga del camino que venga (CAS en {@code operacion_pendiente}). Antes devolvía
 *       el valor bruto de la factura aunque se hubiera descontado el neto, y no devolvía la oferta indirecta (la reversión
 *       entera fallaba).</li>
 *   <li>Solo quien revierte marca la firma y notifica. Si ya estaba revertida, solo completa la firma (sin notificar otra
 *       vez). Sin registro de lo descontado: REVISION_MANUAL y nada cambia.</li>
 * </ol>
 */
@Log
@RequiredArgsConstructor
public class ReversionCuposServiceImpl
        implements ReversionCuposService {

    private final NegociacionOutputPort negociacionPort;
    private final FacturaOutputPort facturaPort;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final NotificacionService notificacionService;
    private final ReversionExactaService reversionExacta;

    @Override
    public void revertirPorFactura(
            String negociacionId,
            String cufe,
            String numeroFactura,
            String actorId,
            String motivo) {

        if (negociacionId == null || negociacionId.isBlank() || cufe == null || cufe.isBlank()) {
            throw EventosSinCufe.rechazar("liquidacion-fallida", negociacionId, numeroFactura);
        }

        Negociacion negociacion =
                negociacionPort
                        .findById(negociacionId)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException(
                                        "Negociación no encontrada"));

        FacturaNegociada enNegociacion = negociacion.getFacturas().stream()
                .filter(f -> cufe.equals(f.getCufe()))
                .findFirst()
                .orElseThrow(() -> new ConflictoException("La factura con ese CUFE no está en la negociación "
                        + negociacionId + ".", ResolutorFactura.FACTURA_NO_COINCIDE));
        String numero = enNegociacion.getNumeroFactura();

        Factura factura = new ResolutorFactura(facturaPort).deLaNegociacion(enNegociacion);

        if (EstadoNegociacion.LIQUIDADA.name().equals(factura.getEstadoNegociacion())
                || EstadoFirma.RECHAZADA.name().equals(factura.getEstadoFirma())) {
            log.info(String.format("Reversión ignorada factura=%s: estado negociación=%s, firma=%s",
                    numero, factura.getEstadoNegociacion(), factura.getEstadoFirma()));
            return;
        }

        ReversionExactaService.Resultado resultado = reversionExacta.revertir(
                TipoOperacionPendiente.LIQUIDACION_FALLIDA, negociacion.getId(), cufe, motivo);

        switch (resultado) {
            case REVERTIDA -> {
                facturaPatchPort.actualizarEstadoFirmaPorCufe(ResolutorFactura.referencia(enNegociacion),
                        EstadoFirma.RECHAZADA);
                notificacionService.notificarLiquidacionFallida(negociacion, numero, motivo);
            }
            case YA_REVERTIDA -> {
                // Otra entrega u otro camino ya devolvió los cupos: solo se completa la firma, sin notificar otra vez.
                log.info(String.format("Reversión ya aplicada factura=%s negociación=%s: no se revierte otra vez",
                        numero, negociacion.getId()));
                facturaPatchPort.actualizarEstadoFirmaPorCufe(ResolutorFactura.referencia(enNegociacion),
                        EstadoFirma.RECHAZADA);
            }
            default -> log.warning(String.format("Reversión de la factura %s de la negociación %s en REVISION_MANUAL: "
                    + "no hay registro de lo descontado", numero, negociacion.getId()));
        }
    }
}
