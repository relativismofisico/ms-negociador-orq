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
     * <p>Fase 28d (A5b, auditoría de escrituras de cupo): el control de cupo suficiente va en la MISMA sentencia
     * ({@code current_quota >= valor}); antes estaba solo en una lectura previa y dos descuentos a la vez dejaban el cupo
     * negativo.</p>
     *
     * @return número de filas afectadas — 0 si la fila de consumo no existe o no tiene cupo suficiente.
     */
    @Modifying
    @Transactional
    @Query("UPDATE IndirectFunderOfferSupplierQuotaEntity e "
            + "SET e.currentQuota = e.currentQuota - :valor "
            + "WHERE e.offerId = :offerId AND e.supplierId = :supplierId AND e.currentQuota >= :valor")
    int descontarCupo(@Param("offerId") Long offerId,
                      @Param("supplierId") Long supplierId,
                      @Param("valor") BigDecimal valor);

    /**
     * Fase 28d (A2): la inversa exacta de {@link #descontarCupo}, atómica en la base de datos; P-22: con tope en el techo
     * ({@code original_quota}, el valor que restaura la recarga): tras una recarga con exposición viva, devolver no lo
     * supera.
     *
     * @return número de filas afectadas — 0 si la fila de consumo no existe.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET current_quota = LEAST(original_quota, current_quota + :valor)"
            + " WHERE offer_id = :offerId AND supplier_id = :supplierId", nativeQuery = true)
    int devolverCupo(@Param("offerId") Long offerId,
                     @Param("supplierId") Long supplierId,
                     @Param("valor") BigDecimal valor);

    /**
     * Fase 28d (P-20): recarga en UNA sentencia: el disponible vuelve al techo ({@code original_quota}). Antes se leía la
     * fila y se guardaba entera con JPA, y lo que otro escribiera entre la lectura y el guardado se perdía.
     *
     * @return filas afectadas (0 si la fila no existe)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET current_quota = original_quota"
            + " WHERE offer_id = :offerId AND supplier_id = :supplierId", nativeQuery = true)
    int recargar(@Param("offerId") Long offerId, @Param("supplierId") Long supplierId);
}
