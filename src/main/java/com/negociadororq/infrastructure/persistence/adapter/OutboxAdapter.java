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
    public boolean guardarEventoConId(String idEvento, String aggregateId, String tipoEvento, Object evento) {
        if (repository.existsById(idEvento)) {
            return false;
        }
        try {
            // insert, no save: con un id existente save lo sobrescribiría (y lo volvería a enviar).
            repository.insert(entidad(idEvento, aggregateId, tipoEvento, evento));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean reencolar(String idEvento) {
        return repository.findById(idEvento).map(e -> {
            e.setEnviado(false);
            e.setFallido(false);
            e.setMotivoFallo(null);
            e.setFechaEnvio(null);
            e.setIntentos(0);
            repository.save(e);
            return true;
        }).orElse(false);
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
