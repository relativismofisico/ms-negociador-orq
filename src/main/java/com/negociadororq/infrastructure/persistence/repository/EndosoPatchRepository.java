package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.infrastructure.persistence.entity.EndosoDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Actualizaciones parciales (patch) de {@link EndosoDocument} vía {@link MongoTemplate},
 * fuera del vocabulario de derivación de Spring Data.
 */
@Repository
public class EndosoPatchRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * @param mongoTemplate plantilla Mongo usada para las actualizaciones parciales
     */
    public EndosoPatchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param estadoFirma   nuevo estado del endoso
     */
    public void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estadoFirma) {

        Query query = Query.query(
                Criteria.where("negociacionId").is(idNegociacion)
                        .and("numerosFactura").is(numeroFactura));

        Update update = new Update()
                .set("estado", estadoFirma.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, EndosoDocument.class);
    }

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param aprobacion    aprobación del actor a registrar (reemplaza una previa del mismo actor)
     * @param estado        nuevo estado del endoso tras registrar la aprobación
     */
    public void registrarAprobacionActor(
            String idNegociacion,
            String numeroFactura,
            AprobacionActorDto aprobacion,
            EstadoEndoso estado
    ) {

        Query query = Query.query(
                Criteria.where("negociacionId").is(idNegociacion)
                        .and("numerosFactura").is(numeroFactura)
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
