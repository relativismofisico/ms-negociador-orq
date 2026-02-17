package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.AprobacionNegociacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AprobacionNegociacionRepository extends MongoRepository<AprobacionNegociacion, String> {
    List<AprobacionNegociacion> findByNegociacionId(String negociacionId);
}
