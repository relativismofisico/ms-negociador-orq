package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.infrastructure.persistence.entity.EndosoDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class EndosoPatchRepository {

    private final MongoTemplate mongoTemplate;

    public EndosoPatchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estadoFirma) {

        Query query = Query.query(
                Criteria.where("negociacionId").is(idNegociacion)
                        .and("numeroFactura").is(numeroFactura));

        Update update = new Update()
                .set("estado", estadoFirma.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, EndosoDocument.class);
    }

    public void registrarAprobacionActor(
            String idNegociacion,
            String numeroFactura,
            AprobacionActorDto aprobacion,
            EstadoEndoso estado
    ) {

        Query query = Query.query(
                Criteria.where("negociacionId").is(idNegociacion)
                        .and("numeroFactura").is(numeroFactura)
        );

        Update removeUpdate = new Update()
                .pull("aprobaciones",
                        new Document("rutActor", aprobacion.getRutActor())
                );

        mongoTemplate.updateFirst(query, removeUpdate, EndosoDocument.class);

        Update addUpdate = new Update()
                .push("aprobaciones", aprobacion)
                .set("estado", estado.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, addUpdate, EndosoDocument.class);
    }
}
