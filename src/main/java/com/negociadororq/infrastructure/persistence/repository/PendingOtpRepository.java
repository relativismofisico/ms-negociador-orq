package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PendingOtpRepository extends MongoRepository<PendingOtpDocument, String> {

    List<PendingOtpDocument> findByIdNegociacion(String idNegociacion);

    void deleteByIdNegociacion(String idNegociacion);
}

