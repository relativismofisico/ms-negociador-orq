package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Spring Data MongoDB de {@link OutboxEventEntity}.
 */
public interface OutboxEventRepository extends MongoRepository<OutboxEventEntity, String> {

    /**
     * @return hasta 50 eventos pendientes de envío, del más antiguo al más nuevo
     */
    List<OutboxEventEntity> findTop50ByEnviadoFalseOrderByFechaCreacionAsc();
}

