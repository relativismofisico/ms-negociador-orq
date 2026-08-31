package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link IndirectFunderOfferEntity}.
 */
public interface IndirectFunderOfferRepository extends JpaRepository<IndirectFunderOfferEntity, Long> {

    // Usada al validar el fondeador indirecto elegido para una NUEVA negociación:
    // exige estado APROBADO (vía servicio) y que la oferta siga activa.
    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @param estado   estado exigido de la oferta (p. ej. APROBADO)
     * @return la oferta activa en ese estado, si existe
     */
    Optional<IndirectFunderOfferEntity> findByFunderIdAndPayerIdAndEstadoAndActivoTrue(
            Long funderId, Long payerId, String estado);
}
