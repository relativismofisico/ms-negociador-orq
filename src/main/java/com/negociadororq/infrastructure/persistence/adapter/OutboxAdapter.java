package com.negociadororq.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.port.output.OutboxOutputPort;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link OutboxOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class OutboxAdapter implements OutboxOutputPort {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public void guardarEvento(String aggregateId, String tipoEvento, Object evento) {
        repository.save(entidad(UUID.randomUUID().toString(), aggregateId, tipoEvento, evento));
    }

    @Override
    public boolean guardarEventoUnico(String eventId, String aggregateId, String tipoEvento, Object evento) {
        try {
            // insert (no save): save() con un id existente reemplazaría el evento, incluso uno ya enviado
            repository.insert(entidad(eventId, aggregateId, tipoEvento, evento));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private OutboxEventEntity entidad(String id, String aggregateId, String tipoEvento, Object evento) {
        try {
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(id);
            entity.setAggregateId(aggregateId);
            entity.setTipoEvento(tipoEvento);
            entity.setPayload(objectMapper.writeValueAsString(evento));
            entity.setFechaCreacion(LocalDateTime.now());
            entity.setEnviado(false);
            entity.setMsPropietario("ms-negociador-orq");
            entity.setIntentos(0);
            return entity;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando evento en outbox", e);
        }
    }
}
