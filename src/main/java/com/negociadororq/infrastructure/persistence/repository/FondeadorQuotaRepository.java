package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link FondeadorQuotaEntity}.
 */
public interface FondeadorQuotaRepository extends JpaRepository<FondeadorQuotaEntity, Long> {

    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @return la asignación fondeador-pagador, si existe
     */
    Optional<FondeadorQuotaEntity> findByFunderIdAndPayerId(Long funderId, Long payerId);

    /**
     * @param funderId   id de la empresa fondeadora
     * @param supplierId id de la empresa proveedora
     * @return la asignación fondeador-proveedor, si existe
     */
    Optional<FondeadorQuotaEntity> findByFunderIdAndSupplierId(Long funderId, Long supplierId);

    // Usadas al validar el fondeador elegido para una NUEVA negociación: si el
    // fondeador desactivó la asignación, no debe poder usarse para negociar
    // facturas nuevas (a diferencia de las reversiones de cupo de una
    // negociación ya en curso, que sí deben poder encontrar la fila aunque se
    // haya desactivado mientras tanto).
    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @return la asignación fondeador-pagador activa, si existe
     */
    Optional<FondeadorQuotaEntity> findByFunderIdAndPayerIdAndActivoTrue(Long funderId, Long payerId);

    /**
     * @param funderId   id de la empresa fondeadora
     * @param supplierId id de la empresa proveedora
     * @return la asignación fondeador-proveedor activa, si existe
     */
    Optional<FondeadorQuotaEntity> findByFunderIdAndSupplierIdAndActivoTrue(Long funderId, Long supplierId);
}

