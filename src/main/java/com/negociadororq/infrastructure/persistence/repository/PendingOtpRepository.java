package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.PendingOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PendingOtpRepository extends MongoRepository<PendingOtp, String> {

    Optional<PendingOtp> findByIdNegociacion(String idNegociacion);

    void deleteByIdNegociacion(String idNegociacion);
}
