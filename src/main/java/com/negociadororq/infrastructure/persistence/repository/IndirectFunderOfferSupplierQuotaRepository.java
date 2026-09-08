package com.negociadororq.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Descuenta {@code valor} del cupo disponible de la fila de consumo del par
     * {@code (offerId, supplierId)} de forma atómica en la base de datos
     * ({@code current_quota = current_quota - valor}), sin leer-modificar-escribir:
     * dos negociaciones concurrentes del mismo par ya no pueden pisarse el
     * resultado.
     *
     * @return número de filas afectadas — 0 si la fila de consumo todavía no existe.
     */
    @Modifying
    @Transactional
    @Query("UPDATE IndirectFunderOfferSupplierQuotaEntity e "
            + "SET e.currentQuota = e.currentQuota - :valor "
            + "WHERE e.offerId = :offerId AND e.supplierId = :supplierId")
    int descontarCupo(@Param("offerId") Long offerId,
                      @Param("supplierId") Long supplierId,
                      @Param("valor") BigDecimal valor);
}
