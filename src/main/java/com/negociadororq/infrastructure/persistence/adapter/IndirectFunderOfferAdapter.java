package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferEntity;
import com.negociadororq.infrastructure.persistence.repository.IndirectFunderOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link IndirectFunderOfferOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class IndirectFunderOfferAdapter implements IndirectFunderOfferOutputPort {

    private static final String ESTADO_APROBADO = "APROBADO";

    private final IndirectFunderOfferRepository repository;

    @Override
    public Optional<IndirectFunderOffer> findActivaAprobada(Long funderId, Long payerId) {
        return repository.findByFunderIdAndPayerIdAndEstadoAndActivoTrue(funderId, payerId, ESTADO_APROBADO)
                .map(this::toDomain);
    }

    @Override
    public Optional<IndirectFunderOffer> findById(Long offerId) {
        return repository.findById(offerId).map(this::toDomain);
    }

    private IndirectFunderOffer toDomain(IndirectFunderOfferEntity entity) {
        return IndirectFunderOffer.builder()
                .id(entity.getId())
                .funderId(entity.getFunderId())
                .payerId(entity.getPayerId())
                .payerRate(entity.getPayerRate())
                .supplierQuotaTemplate(entity.getSupplierQuotaTemplate())
                .estado(entity.getEstado())
                .origen(entity.getOrigen())
                .aprobadoPorFondeador(entity.getAprobadoPorFondeador())
                .activo(entity.getActivo())
                .build();
    }
}
