package com.negociadororq.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.service.OutboxEventPublisher;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;
    private final ObjectMapper objectMapper;


    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void procesarOutbox() {

        log.info("Scheduler ejecutándose...");

        List<OutboxEventEntity> eventos =
                repository.findTop50ByEnviadoFalseOrderByFechaCreacionAsc();

        log.info("Eventos pendientes: {}", eventos.size());

        for (OutboxEventEntity evento : eventos) {

            try {

                LiquidacionSolicitadaEvent event =
                        objectMapper.readValue(evento.getPayload(), LiquidacionSolicitadaEvent.class);

                publisher.publicar(event);

                evento.setEnviado(true);
                evento.setFechaEnvio(LocalDateTime.now());

                repository.save(evento); // 🔥 ESTO ES CLAVE

                log.info("Evento {} enviado correctamente", evento.getId());

            } catch (Exception e) {

                evento.setIntentos(evento.getIntentos() + 1);
                repository.save(evento);

                log.error("Error publicando evento {}", evento.getId(), e);
            }
        }
    }
}
