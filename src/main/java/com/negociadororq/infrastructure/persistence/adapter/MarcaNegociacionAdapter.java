package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Date;
import java.util.Optional;

import com.negociadororq.application.port.output.MarcaNegociacionOutputPort;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * {@link MarcaNegociacionOutputPort} en la colección {@code negociacion_marca}. El {@code _id} (único por definición en
 * Mongo) es el id de la negociación: un segundo marcador con el mismo id choca por clave duplicada. Dentro de una
 * transacción Mongo, el choque aborta esa transacción.
 */
@Component
public class MarcaNegociacionAdapter implements MarcaNegociacionOutputPort {

    static final String COLECCION = "negociacion_marca";

    private final MongoTemplate mongo;

    /**
     * @param mongo plantilla Mongo de la aplicación
     */
    public MarcaNegociacionAdapter(final MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean crear(final String negociacionId, final Tipo tipo) {
        try {
            mongo.insert(new Document("_id", negociacionId).append("tipo", tipo.name()).append("fecha", new Date()),
                    COLECCION);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public Optional<Tipo> buscar(final String negociacionId) {
        final Document d = mongo.findOne(Query.query(Criteria.where("_id").is(negociacionId)), Document.class,
                COLECCION);
        if (d == null) {
            return Optional.empty();
        }
        return Optional.of(Tipo.valueOf(d.getString("tipo")));
    }
}
