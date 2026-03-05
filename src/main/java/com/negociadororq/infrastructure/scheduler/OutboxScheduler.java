package com.negociadororq.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.service.OutboxEventPublisher;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
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

                Object event;

                String tipoEvento = evento.getTipoEvento();

                if (tipoEvento == null) {
                    log.error("Evento {} no tiene tipoEvento", evento.getId());
                    evento.setIntentos(evento.getIntentos() + 1);
                    repository.save(evento);
                    continue;
                }


                switch (tipoEvento) {

                    case "OTP_REQUESTED":
                        event = objectMapper.readValue(
                                evento.getPayload(),
                                OtpRequestEvent.class
                        );
                        break;

                    case "OTP_VALIDATE_REQUEST":
                        event = objectMapper.readValue(
                                evento.getPayload(),
                                OtpValidateRequestEvent.class
                        );
                        break;

                    case "LIQUIDACION_SOLICITADA":
                        event = objectMapper.readValue(
                                evento.getPayload(),
                                LiquidacionSolicitadaEvent.class
                        );
                        break;

                    default:
                        throw new RuntimeException("Evento no soportado: " + tipoEvento);
                }

                publisher.publicar(event);

                evento.setEnviado(true);
                evento.setFechaEnvio(LocalDateTime.now());
                repository.save(evento);

            } catch (Exception e) {

                evento.setIntentos(evento.getIntentos() + 1);
                repository.save(evento);

                log.error("Error publicando evento {}", evento.getId(), e);
            }
        }
    }
}
