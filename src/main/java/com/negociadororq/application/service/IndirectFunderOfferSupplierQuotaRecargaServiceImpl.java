package com.negociadororq.application.service;

import com.negociadororq.application.dto.IndirectFunderOfferSupplierQuotaRecargaDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de {@link IndirectFunderOfferSupplierQuotaRecargaService}.
 */
@RequiredArgsConstructor
public class IndirectFunderOfferSupplierQuotaRecargaServiceImpl implements IndirectFunderOfferSupplierQuotaRecargaService {

    private final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort;

    @Override
    public IndirectFunderOfferSupplierQuotaRecargaDto recargarCupo(Long offerId, Long supplierId) {
        IndirectFunderOfferSupplierQuota cupoProveedor = indirectFunderOfferSupplierQuotaPort
                .find(offerId, supplierId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El proveedor " + supplierId + " todavía no ha negociado contra la oferta " + offerId
                                + ", no hay cupo consumido que recargar"));

        cupoProveedor.setCurrentQuota(cupoProveedor.getOriginalQuota());
        IndirectFunderOfferSupplierQuota guardado = indirectFunderOfferSupplierQuotaPort.save(cupoProveedor);

        return IndirectFunderOfferSupplierQuotaRecargaDto.builder()
                .offerId(guardado.getOfferId())
                .supplierId(guardado.getSupplierId())
                .cupoDisponible(guardado.getCurrentQuota())
                .build();
    }
}
