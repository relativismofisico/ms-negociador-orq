package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repositorio Spring Data MongoDB de {@link OutboxEventEntity}.
 */
public interface OutboxEventRepository extends MongoRepository<OutboxEventEntity, String> {

    /**
     * Pendientes de un propietario, sin los fallidos. El filtro por propietario va en la consulta: los eventos de otros
     * servicios que escriben en la misma colección no ocupan el lote.
     *
     * @param msPropietario servicio dueño de los eventos
     * @param pagina        tamaño del lote y orden (del más antiguo al más nuevo)
     * @return eventos pendientes de envío
     */
    @Query("{ 'msPropietario': ?0, 'enviado': false, 'fallido': { $ne: true } }")
    List<OutboxEventEntity> findPendientes(String msPropietario, Pageable pagina);

    /**
     * @param msPropietario servicio dueño de los eventos
     * @param limite        fecha de envío límite (exclusiva)
     * @return cantidad de eventos enviados borrados
     */
    long deleteByMsPropietarioAndEnviadoTrueAndFechaEnvioBefore(String msPropietario, LocalDateTime limite);
}

