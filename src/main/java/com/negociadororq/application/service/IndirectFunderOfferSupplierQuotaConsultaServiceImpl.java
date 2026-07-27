package com.negociadororq.application.service;

import com.negociadororq.application.dto.IndirectFunderOfferSupplierQuotaDto;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndirectFunderOfferSupplierQuotaConsultaServiceImpl
        implements IndirectFunderOfferSupplierQuotaConsultaService {

    private final PayerFundingVisibilityOutputPort payerFundingVisibilityPort;
    private final IndirectFunderOfferOutputPort indirectFunderOfferPort;
    private final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort;

    @Override
    public IndirectFunderOfferSupplierQuotaDto obtenerCupo(Long funderId, Long payerId, Long supplierId) {

        if (!payerFundingVisibilityPort.estaHabilitado(payerId)) {
            return null;
        }

        IndirectFunderOffer oferta = indirectFunderOfferPort.findActivaAprobada(funderId, payerId).orElse(null);

        if (oferta == null) {
            return null;
        }

        java.math.BigDecimal cupoDisponible = indirectFunderOfferSupplierQuotaPort
                .find(oferta.getId(), supplierId)
                .map(IndirectFunderOfferSupplierQuota::getCurrentQuota)
                .orElse(oferta.getSupplierQuotaTemplate());

        return IndirectFunderOfferSupplierQuotaDto.builder()
                .funderId(funderId)
                .payerId(payerId)
                .supplierId(supplierId)
                .tasa(oferta.getPayerRate())
                .cupoDisponible(cupoDisponible)
                .build();
    }
}
