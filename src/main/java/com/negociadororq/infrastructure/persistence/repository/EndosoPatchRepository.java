package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.enums.EstadoEndoso;
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

    public void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura,  EstadoEndoso estadoFirma) {

        Query query = Query.query(
                Criteria.where("negociacionId").is(idNegociacion)
                        .and("numeroFactura").is(numeroFactura));


        Update update = new Update()
                .set("estado", estadoFirma.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, Endoso.class);
    }

    /**
     * Registrar aprobación/rechazo actor
     */
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

        // eliminar aprobación previa del mismo actor
        Update removeUpdate = new Update()
                .pull("aprobaciones",
                        new org.bson.Document(
                                "rutActor",
                                aprobacion.getRutActor()
                        )
                );

        mongoTemplate.updateFirst(query, removeUpdate, Endoso.class);

        // agregar nueva aprobación
        Update addUpdate = new Update()
                .push("aprobaciones", aprobacion)
                .set("estado", estado.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, addUpdate, Endoso.class);
    }

}
