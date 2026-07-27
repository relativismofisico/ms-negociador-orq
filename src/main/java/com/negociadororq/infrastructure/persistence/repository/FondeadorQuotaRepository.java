package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FondeadorQuotaRepository extends JpaRepository<FondeadorQuotaEntity, Long> {
    Optional<FondeadorQuotaEntity> findByFunderIdAndPayerId(Long funderId, Long payerId);
    Optional<FondeadorQuotaEntity> findByFunderIdAndSupplierId(Long funderId, Long supplierId);

    // Usadas al validar el fondeador elegido para una NUEVA negociación: si el
    // fondeador desactivó la asignación, no debe poder usarse para negociar
    // facturas nuevas (a diferencia de las reversiones de cupo de una
    // negociación ya en curso, que sí deben poder encontrar la fila aunque se
    // haya desactivado mientras tanto).
    Optional<FondeadorQuotaEntity> findByFunderIdAndPayerIdAndActivoTrue(Long funderId, Long payerId);
    Optional<FondeadorQuotaEntity> findByFunderIdAndSupplierIdAndActivoTrue(Long funderId, Long supplierId);
}

