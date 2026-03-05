package com.negociadororq.application.service;

import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.infrastructure.kafka.config.TopicsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TopicsProperties topics;

    public void   publicar(Object event) {

        String topic = null;

        if (event instanceof OtpRequestEvent) {
            topic = topics.getOtpRequest();
        }

        if (event instanceof OtpValidateRequestEvent) {
            topic = topics.getOtpValidate();
        }

        if (event instanceof LiquidacionSolicitadaEvent) {
            topic = topics.getLiquidacion();
        }

        if (topic == null) {
            throw new RuntimeException("No se encontró topic para evento " + event.getClass());
        }

        log.info("Publicando evento en topic {} -> {}", topic, event);

        kafkaTemplate.send(topic, event);
    }
}
