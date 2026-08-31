package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;

import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Actualizaciones parciales (patch) de {@link NegociacionDocument} vía {@link MongoTemplate},
 * fuera del vocabulario de derivación de Spring Data.
 */
@Repository
public class NegociacionPatchRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * @param mongoTemplate plantilla Mongo usada para las actualizaciones parciales
     */
    public NegociacionPatchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param negociacionId id de la negociación
     * @param estadoGlobal  nuevo estado global de la negociación
     */
    public void actualizarEstadoPorId(String negociacionId, EstadoNegociacion estadoGlobal) {
        Query query = Query.query(
                Criteria.where("_id").is(negociacionId)
        );

        Update update = new Update()
                .set("estado", estadoGlobal.name())
                .set("fechaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, NegociacionDocument.class);
    }

    /**
     * @param negociacionId  id de la negociación
     * @param numeroFactura  número de la factura cuyo endoso cambió
     * @param creacionEndoso si ya se creó el endoso para esa factura
     */
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

