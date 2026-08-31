package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link IndirectFunderOfferSupplierQuotaEntity}.
 */
public interface IndirectFunderOfferSupplierQuotaRepository
        extends JpaRepository<IndirectFunderOfferSupplierQuotaEntity, Long> {

    /**
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @return el consumo de ese proveedor contra esa oferta, si existe
     */
    Optional<IndirectFunderOfferSupplierQuotaEntity> findByOfferIdAndSupplierId(Long offerId, Long supplierId);
}
