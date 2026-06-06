package com.negociadororq.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void guardarEvento(String aggregateId,
                              String tipoEvento,
                              Object evento) {

        try {
            OutboxEventEntity entity = new OutboxEventEntity();

            entity.setId(UUID.randomUUID().toString());
            entity.setAggregateId(aggregateId);
            entity.setTipoEvento(tipoEvento);
            entity.setPayload(objectMapper.writeValueAsString(evento));
            entity.setFechaCreacion(LocalDateTime.now());
            entity.setEnviado(false);
            entity.setMsPropietario("ms-negociador-orq");
            entity.setIntentos(0);

            repository.save(entity);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando evento en outbox", e);
        }
    }
}

