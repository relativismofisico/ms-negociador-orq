package com.negociadororq.infrastructure.persistence.adapter;

import java.util.function.Supplier;

import com.negociadororq.application.port.output.TransaccionMongoOutputPort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TransaccionMongoOutputPort} con un {@link MongoTransactionManager} propio, sobre la misma
 * {@link MongoDatabaseFactory} que usan {@code MongoTemplate} y los repositorios (participan de la sesión de la
 * transacción). No se expone como bean: el único {@code PlatformTransactionManager} de la aplicación sigue siendo el de
 * JPA, así que ningún {@code @Transactional} queda ambiguo. Requiere replica set.
 */
@Component
public class TransaccionMongoAdapter implements TransaccionMongoOutputPort {

    private final TransactionTemplate plantilla;

    /**
     * @param fabrica la fábrica de bases de Mongo de la aplicación
     */
    public TransaccionMongoAdapter(final MongoDatabaseFactory fabrica) {
        this.plantilla = new TransactionTemplate(new MongoTransactionManager(fabrica));
    }

    @Override
    public <T> T ejecutar(final Supplier<T> accion) {
        return plantilla.execute(estado -> accion.get());
    }
}
