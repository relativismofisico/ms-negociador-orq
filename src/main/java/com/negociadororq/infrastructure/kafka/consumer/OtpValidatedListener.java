package com.negociadororq.infrastructure.kafka.consumer;

import com.negociadororq.application.service.AprobacionInternaService;
import com.negociadororq.domain.event.OtpValidatedEvent;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpValidatedListener {

    private final PendingOtpRepository pendingOtpRepository;
    private final AprobacionInternaService service;

    @KafkaListener(topics ="${topics.otp-validate-response}",
            groupId = "ms-negociador-orq",
            containerFactory = "otpKafkaListenerContainerFactory")
    public void handle(OtpValidatedEvent event) {

        if (!event.isValido()) {
            log.warn("OTP inválido para negociación {}", event.getIdNegociacion());
            pendingOtpRepository.deleteByIdNegociacion(event.getIdNegociacion());
            return;
        }

        PendingOtp pending = pendingOtpRepository
                .findByIdNegociacion(event.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("No existe solicitud pendiente"));

        pending.setOtp(event.getOtp());
        pending.setIp(event.getIpAprobacion());

        service.aprobarInterno(pending);

        pendingOtpRepository.deleteByIdNegociacion(event.getIdNegociacion());
    }
}
