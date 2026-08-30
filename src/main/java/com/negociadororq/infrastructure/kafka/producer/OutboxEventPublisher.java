package com.negociadororq.infrastructure.kafka.producer;

import java.util.HashMap;
import java.util.Map;

import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.kafka.config.TopicsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en Kafka un evento del patrón Outbox, resolviendo el topic destino según su tipo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TopicsProperties topics;

    private Map<Class<?>, String> topicMap;

    /**
     * Construye el mapa de clase de evento → topic destino, a partir de {@link TopicsProperties}.
     */
    @PostConstruct
    public void init() {
        topicMap = new HashMap<>();
        topicMap.put(OtpRequestEvent.class, topics.getOtpRequest());
        topicMap.put(OtpValidateRequestEvent.class, topics.getOtpValidate());
        topicMap.put(OtpValidateResponseEvent.class, topics.getOtpValidateResponse());
        topicMap.put(LiquidacionSolicitadaEvent.class, topics.getLiquidacion());
        topicMap.put(NotificacionEmailEvent.class, topics.getNotificacionEmail());
    }

    /**
     * @param event evento a publicar; su clase determina el topic destino
     */
    public void publicar(Object event) {
        String topic = topicMap.get(event.getClass());

        if (topic == null) {
            throw new IllegalStateException("No se encontró topic registrado para evento: " + event.getClass().getName());
        }

        log.info("Publicando evento en topic {} -> {}", topic, event);
        kafkaTemplate.send(topic, event);
    }
}
