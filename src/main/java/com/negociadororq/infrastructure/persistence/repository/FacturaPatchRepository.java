package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.application.mapper.EstadoFacturaMapper;
import com.negociadororq.domain.enums.EstadoFactura;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.ReferenciaFactura;
import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Actualizaciones parciales (patch) de {@link FacturaDocument} vía {@link MongoTemplate},
 * fuera del vocabulario de derivación de Spring Data.
 */
@Repository
public class FacturaPatchRepository {

    private final MongoTemplate mongoTemplate;
    private final EstadoFacturaMapper estadoFacturaMapper;

    /**
     * @param mongoTemplate       plantilla Mongo usada para las actualizaciones parciales
     * @param estadoFacturaMapper traduce el estado de negociación al vocabulario de Factura
     */
    public FacturaPatchRepository(MongoTemplate mongoTemplate, EstadoFacturaMapper estadoFacturaMapper) {
        this.mongoTemplate = mongoTemplate;
        this.estadoFacturaMapper = estadoFacturaMapper;
    }

    /**
     * @param facturaId         id de la factura
     * @param estadoFirma       nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación
     */
    public void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion) {
        Query query = Query.query(
                Criteria.where("_id").is(facturaId)
        );

        Update update = new Update()
                .set("estadoFirma", estadoFirma)
                .set("estadoNegociacion", estadoNegociacion.name())
                .set("estadoCreacionEndoso", true)
                .currentDate("fechaActualizacion");

        mongoTemplate.updateFirst(query, update, FacturaDocument.class);
    }

    /**
     * @param facturaId   id de la factura
     * @param estadoFirma nuevo estado de firma
     * @param actores     actores resultantes de la negociación
     */
    public void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores) {
        Query query = Query.query(Criteria.where("_id").is(facturaId));

        Update update = new Update()
                .set("estadoNegociacion", EstadoFactura.NEGOCIADA.name())
                .set("estadoFirma", estadoFirma.name())
                .set("actores", actores)
                .set("fechaNegociacionFactura", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, FacturaDocument.class);
    }

    /**
     * Fase 28d (C): filtro exacto de una factura de una negociación: {@code {cufe, rutProveedor, rutPagador}}.
     */
    private static Query exacta(ReferenciaFactura ref) {
        return Query.query(Criteria.where("cufe").is(ref.cufe())
                .and("rutProveedor").is(ref.rutProveedor())
                .and("rutPagador").is(ref.rutPagador()));
    }

    /**
     * @param ref         factura (CUFE y RUT de la negociación)
     * @param estadoFirma nuevo estado de firma
     * @return {@code true} si una factura coincidió y se actualizó
     */
    public boolean actualizarEstadoFirmaPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma) {
        Update update = new Update()
                .set("estadoFirma", estadoFirma.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());
        return mongoTemplate.findAndModify(exacta(ref), update, FacturaDocument.class) != null;
    }

    /**
     * @param ref               factura (CUFE y RUT de la negociación)
     * @param estadoFirma       nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación, traducido al vocabulario de Factura
     * @return {@code true} si una factura coincidió y se actualizó
     */
    public boolean actualizarEstadosPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma,
                                            EstadoNegociacion estadoNegociacion) {
        Update update = new Update()
                .set("estadoFirma", estadoFirma.name())
                .set("estadoNegociacion", estadoFacturaMapper.aEstadoFactura(estadoNegociacion).name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());
        return mongoTemplate.findAndModify(exacta(ref), update, FacturaDocument.class) != null;
    }

    /**
     * @param ref           factura (CUFE y RUT de la negociación)
     * @param negociacionId id de la negociación a la que pertenece
     * @return {@code true} si una factura coincidió y se actualizó
     */
    public boolean actualizarNegociacionIdPorCufe(ReferenciaFactura ref, String negociacionId) {
        return mongoTemplate.findAndModify(exacta(ref), new Update().set("negociacionId", negociacionId),
                FacturaDocument.class) != null;
    }
}
