package com.negociadororq.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${topics.liquidacion}")
    private String topic;

    public void publicar(Object event) {

        log.info("Publicando evento en Kafka: {}", event);

        kafkaTemplate.send(topic, event);
    }
}
