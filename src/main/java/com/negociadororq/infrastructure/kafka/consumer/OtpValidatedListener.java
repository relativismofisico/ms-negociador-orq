package com.negociadororq.infrastructure.kafka.consumer;

import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.domain.event.OtpValidatedEvent;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Component
@RequiredArgsConstructor
public class OtpValidatedListener {

    private final PendingOtpRepository pendingOtpRepository;
    private final AprobacionEndosoDescuentoService service;

    @KafkaListener(topics ="${topics.otp-validate-response}")
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
