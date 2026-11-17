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

    /**
     * Fase 28d: {@code firmasEmpresa.<clave>} = otpId, con clave = factura y empresa sin puntos ni signos de dólar. El
     * update es condicional: solo si la clave no existe o ya es de este OTP.
     *
     * @param idNegociacion negociación
     * @param numeroFactura factura
     * @param rutEmpresa    empresa
     * @param otpId         OTP de esta firma
     * @return si la firma quedó (o ya estaba) a nombre de este OTP
     */
    public boolean reclamarFirmaEmpresa(String idNegociacion, String numeroFactura, String rutEmpresa, String otpId) {
        String campo = "firmasEmpresa." + (numeroFactura + "|" + rutEmpresa).replace('.', '_').replace('$', '_');
        Query query = Query.query(Criteria.where("negociacionId").is(idNegociacion).and("numerosFactura").is(numeroFactura)
                .orOperator(Criteria.where(campo).exists(false), Criteria.where(campo).is(otpId)));
        return mongoTemplate.findAndModify(query, new Update().set(campo, otpId), EndosoDocument.class) != null;
    }
}
