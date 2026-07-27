package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndirectFunderOfferSupplierQuotaRepository
        extends JpaRepository<IndirectFunderOfferSupplierQuotaEntity, Long> {

    Optional<IndirectFunderOfferSupplierQuotaEntity> findByOfferIdAndSupplierId(Long offerId, Long supplierId);
}
