package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndirectFunderOfferRepository extends JpaRepository<IndirectFunderOfferEntity, Long> {

    // Usada al validar el fondeador indirecto elegido para una NUEVA negociación:
    // exige estado APROBADO (vía servicio) y que la oferta siga activa.
    Optional<IndirectFunderOfferEntity> findByFunderIdAndPayerIdAndEstadoAndActivoTrue(
            Long funderId, Long payerId, String estado);
}
