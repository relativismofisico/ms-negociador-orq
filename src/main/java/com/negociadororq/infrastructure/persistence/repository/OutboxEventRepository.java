package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutboxEventRepository extends MongoRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findTop50ByEnviadoFalseOrderByFechaCreacionAsc();
}
