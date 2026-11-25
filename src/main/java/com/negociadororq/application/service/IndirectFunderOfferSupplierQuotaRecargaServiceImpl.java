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
        // Fase 28d (P-20): una sola sentencia (current_quota = original_quota); antes se leía la fila y se guardaba entera
        // con JPA, y lo que otro escribiera entre la lectura y el guardado se perdía. Recargar sigue restaurando el techo.
        if (indirectFunderOfferSupplierQuotaPort.recargar(offerId, supplierId) == 0) {
            throw new RecursoNoEncontradoException("El proveedor " + supplierId + " todavía no ha negociado contra la "
                    + "oferta " + offerId + ", no hay cupo consumido que recargar");
        }
        IndirectFunderOfferSupplierQuota guardado = indirectFunderOfferSupplierQuotaPort.find(offerId, supplierId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La fila de cupo del proveedor " + supplierId
                        + " en la oferta " + offerId + " ya no existe"));

        return IndirectFunderOfferSupplierQuotaRecargaDto.builder()
                .offerId(guardado.getOfferId())
                .supplierId(guardado.getSupplierId())
                .cupoDisponible(guardado.getCurrentQuota())
                .build();
    }
}
