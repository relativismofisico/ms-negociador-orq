package com.negociadororq.infrastructure.kafka.producer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.springframework.beans.factory.annotation.Value;
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

    /** Espera máxima de la confirmación del broker ({@code outbox.ack-timeout}, 10 s por defecto). */
    // No final: Spring la asigna por @Value después del constructor; el valor inicial sirve a quien lo construya a mano.
    @SuppressWarnings("PMD.ImmutableField")
    @Value("${outbox.ack-timeout:10s}")
    private Duration esperaAck = ESPERA_ACK_POR_DEFECTO;

    private static final Duration ESPERA_ACK_POR_DEFECTO = Duration.ofSeconds(10);

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

        // Solo el tipo de evento y el topic: el evento completo puede llevar destinatarios y data de un correo.
        log.info("Publicando evento {} en topic {}", event.getClass().getSimpleName(), topic);
        // Espera la confirmación del broker: el outbox solo marca enviado lo que Kafka confirmó. Si no llega a tiempo o
        // falla, la excepción deja el evento pendiente y el scheduler suma un intento.
        try {
            kafkaTemplate.send(topic, event).get(esperaAck.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando la confirmación de Kafka (topic " + topic + ")", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka no confirmó el evento (topic " + topic + ")", e);
        }
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
