package com.negociadororq.infrastructure.kafka.consumer;

import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.domain.event.OtpValidatedEvent;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpValidatedListener {

    private final PendingOtpRepository pendingOtpRepository;
    private final AprobacionEndosoDescuentoService service;

    @KafkaListener(topics = "OTP_VALIDATED")
    public void handle(OtpValidatedEvent event) {

        if (!event.isValido()) {
            return;
        }

        PendingOtp pending = pendingOtpRepository
                .findByIdNegociacion(event.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("No existe solicitud pendiente"));

        service.aprobarInterno(pending.getRequest());

        pendingOtpRepository.deleteByIdNegociacion(event.getIdNegociacion());
    }
}
