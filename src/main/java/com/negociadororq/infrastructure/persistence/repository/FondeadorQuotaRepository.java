package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link FondeadorQuotaEntity}.
 *
 * <p>La tabla {@code funder} admite varias filas por par fondeador-pagador o fondeador-proveedor
 * (p. ej. una asignación RECHAZADA o PENDIENTE junto a la APROBADA del mismo par), así que las
 * consultas por par devuelven todas las filas, de la más reciente a la más antigua; la elección de
 * la asignación efectiva la hace {@code FondeadorQuotaAdapter}.</p>
 */
public interface FondeadorQuotaRepository extends JpaRepository<FondeadorQuotaEntity, Long> {

    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @return todas las asignaciones fondeador-pagador, de la más reciente (id mayor) a la más antigua
     */
    List<FondeadorQuotaEntity> findByFunderIdAndPayerIdOrderByIdDesc(Long funderId, Long payerId);

    /**
     * @param funderId   id de la empresa fondeadora
     * @param supplierId id de la empresa proveedora
     * @return todas las asignaciones fondeador-proveedor, de la más reciente (id mayor) a la más antigua
     */
    List<FondeadorQuotaEntity> findByFunderIdAndSupplierIdOrderByIdDesc(Long funderId, Long supplierId);
}
