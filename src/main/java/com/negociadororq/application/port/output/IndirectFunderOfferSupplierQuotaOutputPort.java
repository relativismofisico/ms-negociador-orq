package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;

import java.util.Optional;

/**
 * Puerto para el consumo independiente de cada proveedor contra una oferta
 * de cupo compartido fondeador→pagador. Cada fila se crea de forma perezosa
 * la primera vez que ese proveedor negocia contra esa oferta.
 */
public interface IndirectFunderOfferSupplierQuotaOutputPort {

    /**
     * Busca el consumo de un proveedor contra una oferta, si ya negoció antes.
     *
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @return consumo existente, si ya se creó
     */
    Optional<IndirectFunderOfferSupplierQuota> find(Long offerId, Long supplierId);

    /**
     * Crea la fila de consumo de un proveedor contra una oferta, con el cupo y
     * tasa vigentes de la oferta en ese instante (snapshot).
     *
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @param rate       tasa vigente de la oferta al crear
     * @param quota      cupo vigente de la oferta al crear (íntegro, no repartido)
     * @return el consumo recién creado
     */
    IndirectFunderOfferSupplierQuota crear(Long offerId, Long supplierId,
            java.math.BigDecimal rate, java.math.BigDecimal quota);

    /**
     * Persiste el consumo actualizado (tras descontar cupo).
     *
     * @param quota consumo con el cupo ya descontado
     * @return el consumo persistido
     */
    IndirectFunderOfferSupplierQuota save(IndirectFunderOfferSupplierQuota quota);
}
