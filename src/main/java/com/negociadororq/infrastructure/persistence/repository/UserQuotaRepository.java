package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.UserQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA de {@link UserQuotaEntity}.
 */
@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuotaEntity, Long> {

    /**
     * @param userId id del usuario
     * @return el cupo del usuario, si existe
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    Optional<UserQuotaEntity> findByUser_IdeUser(Long userId);

    /**
     * Fase 28d (A5b): descuento atómico, solo si alcanza.
     *
     * @param userId id del usuario
     * @param monto  monto
     * @return filas afectadas (0 si no alcanza o no existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_quota SET current_quota = current_quota - :monto"
            + " WHERE user_id = :userId AND current_quota >= :monto", nativeQuery = true)
    int descontar(@Param("userId") Long userId, @Param("monto") java.math.BigDecimal monto);

    /**
     * Fase 28d (A5b): devolución atómica.
     *
     * @param userId id del usuario
     * @param monto  monto
     * @return filas afectadas (0 si no existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_quota SET current_quota = current_quota + :monto WHERE user_id = :userId",
            nativeQuery = true)
    int devolver(@Param("userId") Long userId, @Param("monto") java.math.BigDecimal monto);

    /**
     * Fase 28d (A5b): devolución atómica limitada a cero.
     *
     * @param userId id del usuario
     * @param monto  monto
     * @return filas afectadas (0 si no existe)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_quota SET current_quota = GREATEST(current_quota + :monto, 0) WHERE user_id = :userId",
            nativeQuery = true)
    int devolverSinNegativos(@Param("userId") Long userId, @Param("monto") java.math.BigDecimal monto);
}

