package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Spring Data MongoDB de {@link PendingOtpDocument}.
 */
public interface PendingOtpRepository extends MongoRepository<PendingOtpDocument, String> {

    /**
     * @param estado estado de la firma (Fase 28c)
     * @param limite última transición anterior a este instante
     * @return las solicitudes en ese estado sin cambios desde el límite
     */
    List<PendingOtpDocument> findByEstadoAndFechaActualizacionBefore(String estado, LocalDateTime limite);
}

