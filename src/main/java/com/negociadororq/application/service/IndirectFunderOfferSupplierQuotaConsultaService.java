package com.negociadororq.application.service;

import com.negociadororq.application.dto.IndirectFunderOfferSupplierQuotaDto;

/**
 * Consulta de solo lectura del cupo disponible de un proveedor contra una
 * oferta de cupo compartido fondeador→pagador ("factoring indirecto vía
 * pagador"). No crea la fila de consumo — eso solo ocurre al negociar
 * (ver {@code ProcesarFacturaNegociacionServiceImpl}).
 */
public interface IndirectFunderOfferSupplierQuotaConsultaService {

    /**
     * @param funderId   id de la empresa fondeadora
     * @param payerId    id de la empresa pagadora
     * @param supplierId id de la empresa proveedora que consulta su cupo
     * @return tasa y cupo disponible, o {@code null} si el pagador no
     *         habilitó el toggle o no hay oferta aprobada vigente
     */
    IndirectFunderOfferSupplierQuotaDto obtenerCupo(Long funderId, Long payerId, Long supplierId);

}
