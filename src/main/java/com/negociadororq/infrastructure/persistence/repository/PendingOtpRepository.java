package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Spring Data MongoDB de {@link PendingOtpDocument}.
 */
public interface PendingOtpRepository extends MongoRepository<PendingOtpDocument, String> {

    /**
     * @param idNegociacion id de la negociación
     * @return las solicitudes OTP pendientes para esa negociación
     */
    List<PendingOtpDocument> findByIdNegociacion(String idNegociacion);

    /**
     * @param idNegociacion id de la negociación cuyas solicitudes OTP se eliminan
     */
    void deleteByIdNegociacion(String idNegociacion);
}

