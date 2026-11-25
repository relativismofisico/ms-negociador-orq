package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA de {@link EmpresaQuotaEntity}.
 */
@Repository
public interface EmpresaQuotaRepository extends JpaRepository<EmpresaQuotaEntity, Long> {

    /**
     * @param companyId id de la empresa
     * @return el cupo de la empresa, si existe
     */
    Optional<EmpresaQuotaEntity> findByCompanyId(Long companyId);

    /**
     * Fase 28d (modelo de cupos de John): descuenta el disponible de la empresa en UNA sentencia condicionada (sin leer,
     * modificar y escribir); 0 filas = no alcanza. Va en la transacción de la factura, junto con el del operador.
     *
     * @param companyId empresa
     * @param valor     monto
     * @return filas afectadas
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "UPDATE parametrization SET current_quota = current_quota - :valor,"
            + " version = COALESCE(version, 0) + 1 WHERE company_id = :companyId AND current_quota >= :valor",
            nativeQuery = true)
    int descontar(@org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("valor") java.math.BigDecimal valor);

    /**
     * Fase 28d (P-22): devuelve con tope en el techo, {@code MIN(quota, current_quota + valor)}, en una sentencia.
     *
     * @param companyId empresa
     * @param valor     monto
     * @return filas afectadas (0 si la empresa no tiene cupo)
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "UPDATE parametrization SET current_quota = LEAST(quota,"
            + " current_quota + :valor), version = COALESCE(version, 0) + 1 WHERE company_id = :companyId"
            + " AND objetivo IS NULL",
            nativeQuery = true)
    int devolverConTope(@org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("valor") java.math.BigDecimal valor);
}
