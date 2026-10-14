package com.negociadororq.infrastructure.kafka.producer;

import java.util.HashMap;
import java.util.Map;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.factoring.commons.auditoria.AuditoriaPublisher;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.kafka.config.TopicsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<AuditoriaPublisher> auditoriaPublisher;

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
        if (event instanceof AuditoriaAccionEvent auditoria) {
            publicarAuditoria(auditoria);
            return;
        }
        String topic = topicMap.get(event.getClass());

        if (topic == null) {
            throw new IllegalStateException("No se encontró topic registrado para evento: " + event.getClass().getName());
        }

        log.info("Publicando evento en topic {} -> {}", topic, event);
        kafkaTemplate.send(topic, event);
    }

    /**
     * Auditoría: con su propio publicador (clave por entidad, JSON sin headers de tipo) y esperando la confirmación
     * del broker; si falla, la excepción deja el evento pendiente en el outbox y el scheduler lo reintenta. Con
     * {@code auditoria.enabled=false} no hay publicador y se descarta.
     */
    private void publicarAuditoria(AuditoriaAccionEvent evento) {
        AuditoriaPublisher publicador = auditoriaPublisher.getIfAvailable();
        if (publicador == null) {
            log.warn("Auditoría desactivada: se descarta el evento {}", evento.eventId());
            return;
        }
        publicador.publicarConfirmado(evento);
    }
}
