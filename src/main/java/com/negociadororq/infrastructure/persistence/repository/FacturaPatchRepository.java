package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** Fase 28d (A5): resultado de una escritura condicionada a que la factura no esté VENCIDA. */
    public enum Resultado {
        /** La factura coincidió y se actualizó. */
        ACTUALIZADA,
        /** La factura existe pero ya está VENCIDA (el job ganó): no se tocó. */
        VENCIDA,
        /** Ninguna factura con ese CUFE y esas empresas. */
        NO_COINCIDE
    }

    static final String CAMPO_ESTADO = "estadoNegociacion";
    static final String CAMPO_FIRMA = "estadoFirma";
    static final String CAMPO_ACTUALIZACION = "fechaUltimaActualizacion";
    static final String VENCIDA = EstadoFactura.VENCIDA.name();
    /** Firmas anteriores a la liquidación (tabla de A5, filas 1 a 4). */
    public static final List<String> FIRMAS_ANTES_DE_LIQUIDAR = List.of(EstadoFirma.SIN_CESION.name(),
            EstadoFirma.SIN_DESCUENTO.name(), EstadoFirma.EN_REVISION.name(), EstadoFirma.EN_APROBACION.name());
    /** Estados terminales de la factura (tabla de A5, filas 7 a 11): la firma final no los revive. */
    public static final List<String> ESTADOS_TERMINALES = List.of(VENCIDA, EstadoFactura.RECHAZADA.name(),
            EstadoFactura.LIQUIDADA.name(), EstadoFactura.BLOQUEADA.name(), EstadoFactura.VENDIDA.name(), "INEXISTENTE");

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
        // Fase 28d (A5): una factura VENCIDA no vuelve a ENDOSADA.
        Query query = Query.query(
                Criteria.where("_id").is(facturaId).and(CAMPO_ESTADO).ne(VENCIDA)
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
     * Fase 28d (A5): como {@link #actualizarEstadosPorCufe}, pero sin tocar una factura VENCIDA.
     *
     * @param ref               factura (CUFE y RUT de la negociación)
     * @param estadoFirma       nuevo estado de firma
     * @param estadoNegociacion nuevo estado de negociación, traducido al vocabulario de Factura
     * @return si se actualizó, si estaba VENCIDA o si no coincidió ninguna
     */
    public Resultado actualizarEstadosSiNoVencida(ReferenciaFactura ref, EstadoFirma estadoFirma,
                                                  EstadoNegociacion estadoNegociacion) {
        Query query = exacta(ref).addCriteria(Criteria.where(CAMPO_ESTADO).ne(VENCIDA));
        Update update = new Update()
                .set(CAMPO_FIRMA, estadoFirma.name())
                .set(CAMPO_ESTADO, estadoFacturaMapper.aEstadoFactura(estadoNegociacion).name())
                .set(CAMPO_ACTUALIZACION, LocalDateTime.now());
        if (mongoTemplate.findAndModify(query, update, FacturaDocument.class) != null) {
            return Resultado.ACTUALIZADA;
        }
        if (mongoTemplate.exists(exacta(ref), FacturaDocument.class)) {
            return Resultado.VENCIDA;
        }
        return Resultado.NO_COINCIDE;
    }

    /**
     * Fase 28d (A5): firma final y marca de la solicitud de liquidación, condicionadas (compare-and-set) a que la
     * factura no esté en un estado terminal (VENCIDA incluida) y su firma sea anterior a la liquidación o ya la final
     * (reintento de la misma aprobación). Se llama dentro de la transacción Mongo que escribe también el evento.
     *
     * @param ref        factura (CUFE y RUT de la negociación)
     * @param firmaFinal CESION (FACTORING) o DESCUENTO (CONFIRMING)
     * @param idEvento   id del evento {@code liquidacion.solicitada}
     * @return {@code true} si ganó (la factura queda NEGOCIADA con la firma final)
     */
    public boolean marcarFirmaFinalConSolicitud(ReferenciaFactura ref, EstadoFirma firmaFinal, String idEvento) {
        List<String> firmas = new ArrayList<>(FIRMAS_ANTES_DE_LIQUIDAR);
        firmas.add(firmaFinal.name());
        Query query = exacta(ref).addCriteria(Criteria.where(CAMPO_ESTADO).nin(ESTADOS_TERMINALES)
                .and(CAMPO_FIRMA).in(firmas));
        Update update = new Update()
                .set(CAMPO_FIRMA, firmaFinal.name())
                .set(CAMPO_ESTADO, EstadoFactura.NEGOCIADA.name())
                .set("solicitudLiquidacionId", idEvento)
                .set("seguimientoLiquidacionPendiente", true)
                .set(CAMPO_ACTUALIZACION, LocalDateTime.now());
        return mongoTemplate.findAndModify(query, update, FacturaDocument.class) != null;
    }

    /**
     * Fase 28d (A5): el job marca VENCIDA solo si la factura sigue exactamente como la leyó (compare-and-set con la
     * firma final de la aprobación: gana uno).
     *
     * @param facturaId          id de la factura
     * @param estadoLeido        estado de negociación leído
     * @param firmaLeida         estado de firma leído (puede ser nulo)
     * @param reversionPendiente si queda pendiente devolver los cupos
     * @return {@code true} si la marcó
     */
    public boolean marcarVencidaSiCoincide(String facturaId, String estadoLeido, String firmaLeida,
                                           boolean reversionPendiente) {
        Query query = Query.query(Criteria.where("_id").is(facturaId).and(CAMPO_ESTADO).is(estadoLeido)
                .and(CAMPO_FIRMA).is(firmaLeida));
        Update update = new Update()
                .set(CAMPO_ESTADO, VENCIDA)
                .set("vencimientoReversionPendiente", reversionPendiente)
                .set(CAMPO_ACTUALIZACION, LocalDateTime.now());
        return mongoTemplate.findAndModify(query, update, FacturaDocument.class) != null;
    }

    /**
     * @param facturaId Fase 28d (A5): la reversión del vencimiento terminó
     */
    public void quitarReversionPendiente(String facturaId) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(facturaId)),
                new Update().unset("vencimientoReversionPendiente"), FacturaDocument.class);
    }

    /**
     * @param ref Fase 28d (A5): el seguimiento de la solicitud ya consta en MySQL
     */
    public void quitarSeguimientoLiquidacionPendiente(ReferenciaFactura ref) {
        mongoTemplate.updateFirst(exacta(ref), new Update().unset("seguimientoLiquidacionPendiente"),
                FacturaDocument.class);
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
