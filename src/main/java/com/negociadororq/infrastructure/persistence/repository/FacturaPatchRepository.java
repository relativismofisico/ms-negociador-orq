package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.enums.EstadoFactura;
import com.negociadororq.enums.EstadoFirma;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FacturaPatchRepository {

    private final MongoTemplate mongoTemplate;

    public FacturaPatchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma) {
        Query query = Query.query(
                Criteria.where("_id").is(facturaId)
        );

        Update update = new Update()
                .set("estadoFirma", estadoFirma)
                .currentDate("fechaActualizacion");

        mongoTemplate.updateFirst(query, update, Factura.class);
    }

    public void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores) {
        Query query = Query.query(Criteria.where("_id").is(facturaId));

        Update update = new Update()
                .set("estadoNegociacion", EstadoFactura.NEGOCIADA.name())
                .set("estadoFirma", estadoFirma.name())
                .set("actores", actores)
                .set("fechaNegociacionFactura", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, Factura.class);
    }

    public void actualizarEstadoFirmaPorNoFactura(String numeroFactura, EstadoFirma estadoFirma) {

        Query query = Query.query(
                Criteria.where("numeroFactura").is(numeroFactura));

        Update update = new Update()
                .set("estadoFirma", estadoFirma.name())
                .set("fechaUltimaActualizacion", LocalDateTime.now());

        mongoTemplate.updateFirst(query, update, Factura.class);
    }

}
