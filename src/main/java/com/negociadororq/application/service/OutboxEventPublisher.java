package com.negociadororq.application.service;

import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.kafka.config.TopicsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TopicsProperties topics;

    private Map<Class<?>, String> topicMap;

    @PostConstruct
    public void init() {

        topicMap = new HashMap<>();

        topicMap.put(OtpRequestEvent.class, topics.getOtpRequest());
        topicMap.put(OtpValidateRequestEvent.class, topics.getOtpValidate());
        topicMap.put(OtpValidateResponseEvent.class, topics.getOtpValidateResponse());
        topicMap.put(LiquidacionSolicitadaEvent.class, topics.getLiquidacion());
    }

    public void publicar(Object event) {

        String topic = topicMap.get(event.getClass());

        if (topic == null) {
            throw new RuntimeException("No se encontró topic para evento " + event.getClass());
        }

        log.info("Publicando evento en topic {} -> {}", topic, event);

        kafkaTemplate.send(topic, event);
    }
}