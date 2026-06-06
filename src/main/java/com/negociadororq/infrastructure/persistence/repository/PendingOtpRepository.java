package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PendingOtpRepository extends MongoRepository<PendingOtpDocument, String> {

    Optional<PendingOtpDocument> findByIdNegociacion(String idNegociacion);

    void deleteByIdNegociacion(String idNegociacion);
}

