package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.EstadoNegociacion;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class NegociacionPatchRepository {

    private final MongoTemplate mongoTemplate;

    public NegociacionPatchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void actualizarEstadoPorId(Negociacion negociacion, EstadoNegociacion estadoGlobal) {
        Query query = Query.query(
                Criteria.where("_id").is(negociacion.getId())
        );

        Update update = new Update()
                .set("estado", estadoGlobal.name())
                .set("fechaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, Negociacion.class);
    }

}
