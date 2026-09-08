package com.negociadororq.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.util.Optional;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.IndirectFunderOfferSupplierQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link IndirectFunderOfferSupplierQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class IndirectFunderOfferSupplierQuotaAdapter implements IndirectFunderOfferSupplierQuotaOutputPort {

    private final IndirectFunderOfferSupplierQuotaRepository repository;

    @Override
    public Optional<IndirectFunderOfferSupplierQuota> find(Long offerId, Long supplierId) {
        return repository.findByOfferIdAndSupplierId(offerId, supplierId).map(this::toDomain);
    }

    @Override
    public IndirectFunderOfferSupplierQuota crear(Long offerId, Long supplierId, BigDecimal rate, BigDecimal quota) {
        IndirectFunderOfferSupplierQuotaEntity entity = IndirectFunderOfferSupplierQuotaEntity.builder()
                .offerId(offerId)
                .supplierId(supplierId)
                .rate(rate)
                .quota(quota)
                .currentQuota(quota)
                .originalQuota(quota)
                .build();

        try {
            return toDomain(repository.save(entity));
        } catch (DataIntegrityViolationException e) {
            // El UNIQUE (offer_id, supplier_id) de la tabla lo cubrió otra
            // negociación concurrente entre nuestro chequeo y este INSERT. Se
            // traduce a un error de negocio legible, sin filtrar tabla/constraint.
            throw new ConflictoException(
                    "El proveedor ya tiene un cupo abierto contra esta oferta de fondeo indirecta "
                            + "(creación concurrente). Vuelve a intentar.",
                    "CUPO_PROVEEDOR_CONCURRENCIA");
        }
    }

    @Override
    public int descontarCupo(Long offerId, Long supplierId, BigDecimal valor) {
        return repository.descontarCupo(offerId, supplierId, valor);
    }

    @Override
    public IndirectFunderOfferSupplierQuota save(IndirectFunderOfferSupplierQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    private IndirectFunderOfferSupplierQuota toDomain(IndirectFunderOfferSupplierQuotaEntity entity) {
        return IndirectFunderOfferSupplierQuota.builder()
                .id(entity.getId())
                .offerId(entity.getOfferId())
                .supplierId(entity.getSupplierId())
                .rate(entity.getRate())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .originalQuota(entity.getOriginalQuota())
                .build();
    }

    private IndirectFunderOfferSupplierQuotaEntity toEntity(IndirectFunderOfferSupplierQuota quota) {
        return IndirectFunderOfferSupplierQuotaEntity.builder()
                .id(quota.getId())
                .offerId(quota.getOfferId())
                .supplierId(quota.getSupplierId())
                .rate(quota.getRate())
                .quota(quota.getQuota())
                .currentQuota(quota.getCurrentQuota())
                .originalQuota(quota.getOriginalQuota())
                .build();
    }
}
