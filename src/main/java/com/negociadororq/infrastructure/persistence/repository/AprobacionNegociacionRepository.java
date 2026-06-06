package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.AprobacionNegociacionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AprobacionNegociacionRepository extends MongoRepository<AprobacionNegociacionDocument, String> {
    List<AprobacionNegociacionDocument> findByNegociacionId(String negociacionId);
}
