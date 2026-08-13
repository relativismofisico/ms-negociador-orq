package com.negociadororq.application.service;

import com.negociadororq.application.dto.IndirectFunderOfferSupplierQuotaRecargaDto;

/**
 * Recarga del cupo consumido de un proveedor puntual contra una oferta de
 * cupo compartido fondeador→pagador ("factoring indirecto vía pagador").
 * Acción disparada por el fondeador, a diferencia de la consulta de solo
 * lectura en {@link IndirectFunderOfferSupplierQuotaConsultaService}, que la
 * dispara el propio proveedor.
 */
public interface IndirectFunderOfferSupplierQuotaRecargaService {

    /**
     * Restaura el cupo disponible del proveedor a su valor original (el cupo
     * vigente de la oferta cuando ese proveedor negoció por primera vez), sin
     * afectar a otros proveedores ni al cupo íntegro (supplierQuotaTemplate)
     * de la oferta.
     *
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @return el cupo recargado
     */
    IndirectFunderOfferSupplierQuotaRecargaDto recargarCupo(Long offerId, Long supplierId);
}
