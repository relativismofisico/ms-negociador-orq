package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
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

    public void actualizarEstadoPorId(String negociacionId, EstadoNegociacion estadoGlobal) {
        Query query = Query.query(
                Criteria.where("_id").is(negociacionId)
        );

        Update update = new Update()
                .set("estado", estadoGlobal.name())
                .set("fechaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, NegociacionDocument.class);
    }

    public void actualizarEstadoEndosoPorId(String negociacionId, String numeroFactura, Boolean creacionEndoso) {
        Query query = Query.query(
                Criteria.where("_id").is(negociacionId)
                        .and("facturas.numeroFactura").is(numeroFactura)
        );

        Update update = new Update()
                .set("facturas.$.estadoCreacionEndoso", creacionEndoso)
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, NegociacionDocument.class);
    }
}

