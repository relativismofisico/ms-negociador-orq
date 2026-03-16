package com.negociadororq.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.service.OutboxEventPublisher;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private Map<String, Class<?>> eventMap;
    private static final int MAX_INTENTOS = 5;

    @PostConstruct
    public void init() {

        eventMap = new HashMap<>();

        eventMap.put(topicRequestOtp, OtpRequestEvent.class);
        eventMap.put(topicValidateOtp, OtpValidateRequestEvent.class);
        eventMap.put(topicValidateResponse, OtpValidateResponseEvent.class);
        eventMap.put(liquidacionFactura, LiquidacionSolicitadaEvent.class);
        log.info("OutboxScheduler inicializado con eventos: {}", eventMap.keySet());
    }


    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void procesarOutbox() {

        log.info("Scheduler ejecutándose...");

        List<OutboxEventEntity> eventos =
                repository.findTop50ByEnviadoFalseOrderByFechaCreacionAsc();

        log.info("Eventos pendientes: {}", eventos.size());

        for (OutboxEventEntity evento : eventos) {

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
