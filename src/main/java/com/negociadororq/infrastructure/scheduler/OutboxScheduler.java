package com.negociadororq.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica periódicamente los eventos pendientes del patrón Outbox, reintentando hasta un máximo
 * de intentos y descartando los eventos que no pertenecen a este servicio o que ya no reconoce.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Value("${topics.otp-request}")
    private String topicRequestOtp;

    @Value("${topics.otp-validate}")
    private String topicValidateOtp;

    @Value("${topics.otp-validate-response}")
    private String topicValidateResponse;

    @Value("${topics.liquidacion}")
    private String liquidacionFactura;

    @Value("${topics.notificacion-email}")
    private String notificacionEmail;

    @Value("${auditoria.topic:auditoria.accion}")
    private String topicAuditoria;

    // Propietario de este MS
    private static final String MS_PROPIETARIO = "ms-negociador-orq";

    private Map<String, Class<?>> eventMap;
    private static final int MAX_INTENTOS = 5;

    /**
     * Construye el mapa de topic → clase de evento usado para deserializar el payload guardado.
     */
    @PostConstruct
    public void init() {

        eventMap = new HashMap<>();

        eventMap.put(topicRequestOtp, OtpRequestEvent.class);
        eventMap.put(topicValidateOtp, OtpValidateRequestEvent.class);
        eventMap.put(topicValidateResponse, OtpValidateResponseEvent.class);
        eventMap.put(liquidacionFactura, LiquidacionSolicitadaEvent.class);
        eventMap.put(notificacionEmail, NotificacionEmailEvent.class);
        eventMap.put(topicAuditoria, AuditoriaAccionEvent.class);
        log.info("OutboxScheduler inicializado con eventos: {}", eventMap.keySet());
    }


    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "ms-negociador-orq_outboxScheduler_procesarOutbox",
            lockAtLeastFor = "PT4S", lockAtMostFor = "PT55S")
    @Transactional
    public void procesarOutbox() {

        log.info("Scheduler ejecutándose...");

        List<OutboxEventEntity> eventos =
                repository.findTop50ByEnviadoFalseOrderByFechaCreacionAsc();

        log.info("Eventos pendientes: {}", eventos.size());

        for (OutboxEventEntity evento : eventos) {
            // 🔹 Filtrar por propietario
            if (!MS_PROPIETARIO.equals(evento.getMsPropietario())) {
                log.info("Evento {} ignorado, pertenece a {}", evento.getId(), evento.getMsPropietario());
                continue;
            }

            try {

                if (evento.getIntentos() >= MAX_INTENTOS) {

                    log.error("Evento {} superó el máximo de intentos", evento.getId());

                    marcarComoEnviado(evento);
                    continue;
                }

                String tipoEvento = evento.getTipoEvento();

                if (tipoEvento == null) {

                    log.error("Evento {} no tiene tipoEvento", evento.getId());

                    incrementarIntentos(evento);
                    continue;
                }

                Class<?> eventClass = eventMap.get(tipoEvento);

                if (eventClass == null) {

                    log.info("Evento ignorado por ms-negociador-orq: {}", tipoEvento);

                    marcarComoEnviado(evento);
                    continue;
                }

                Object event = objectMapper.readValue(
                        evento.getPayload(),
                        eventClass
                );

                publisher.publicar(event);

                marcarComoEnviado(evento);

                log.info("Evento {} publicado correctamente", evento.getId());

            } catch (Exception e) {

                incrementarIntentos(evento);

                log.error("Error publicando evento {}", evento.getId(), e);
            }
        }
    }

    private void marcarComoEnviado(OutboxEventEntity evento) {

        evento.setEnviado(true);
        evento.setFechaEnvio(LocalDateTime.now());

        repository.save(evento);
    }

    private void incrementarIntentos(OutboxEventEntity evento) {

        evento.setIntentos(evento.getIntentos() + 1);

        repository.save(evento);
    }


}

