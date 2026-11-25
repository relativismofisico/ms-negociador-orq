package com.negociadororq.application.service;

import java.math.BigDecimal;

import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort.Propagacion;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Fase 28d (P-19, decisión de John J-09): cuando el fondeador cambia el cupo por proveedor de su oferta indirecta, los
 * proveedores que YA negociaron esa oferta reciben el ajuste.
 * <ul>
 *   <li>Subir en d: cada uno suma d a su techo y a su disponible.</li>
 *   <li>Bajar: nunca por debajo de lo consumido: techo = MAX(techo + d, consumido) y disponible = techo - consumido.
 *       Quien había consumido más que el cupo nuevo queda con un techo mayor que la plantilla ("exceso", se reporta).</li>
 *   <li>Los que aún no negocian toman el valor vigente en su primera negociación; la recarga restaura el techo ajustado.</li>
 * </ul>
 * La idempotencia por evento la pone el consumidor (ConsumoIdempotente); la versión de cada fila descarta, además,
 * reentregas y eventos viejos que llegan después de uno nuevo.
 */
@Log
@RequiredArgsConstructor
public class PropagacionCupoOfertaService {

    private final IndirectFunderOfferSupplierQuotaOutputPort cupos;

    /**
     * @param offerId oferta
     * @param version versión del cambio
     * @param nuevo   cupo por proveedor nuevo (obligatorio, no negativo)
     * @return filas ajustadas y cuántas quedaron por encima de la plantilla
     */
    public Propagacion aplicar(Long offerId, long version, BigDecimal nuevo) {
        if (offerId == null || nuevo == null || nuevo.signum() < 0) {
            throw new IllegalArgumentException("Cambio de cupo de oferta inválido (oferta " + offerId + ", versión "
                    + version + ")");
        }
        Propagacion p = cupos.propagarCupoOferta(offerId, version, nuevo);
        log.info(() -> "[CUPO-OFERTA-PROPAGADO] oferta " + offerId + " versión " + version + ": " + p.ajustadas()
                + " proveedores ajustados");
        if (p.porEncima() > 0) {
            log.warning(() -> "[CUPO-OFERTA-EXCESO] oferta " + offerId + " versión " + version + ": " + p.porEncima()
                    + " proveedores quedaron con un techo mayor que el cupo por proveedor nuevo (ya habían consumido"
                    + " más); su disponible es 0 y su techo baja solo con un cambio posterior de la oferta");
        }
        return p;
    }
}
