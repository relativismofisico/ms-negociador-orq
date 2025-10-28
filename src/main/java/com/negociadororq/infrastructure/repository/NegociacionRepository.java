package com.negociadororq.infrastructure.repository;

import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.Negociacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NegociacionRepository extends MongoRepository<Negociacion, String> {
}
