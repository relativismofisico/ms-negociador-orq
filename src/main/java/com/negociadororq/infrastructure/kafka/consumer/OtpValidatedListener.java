package com.negociadororq.infrastructure.kafka.consumer;

import com.negociadororq.application.service.FirmaOtpService;
import com.negociadororq.domain.event.OtpValidatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code otp.validate.response} de ms-security-util y delega en {@link FirmaOtpService} (Fase 28c: estados
 * de la firma, intentos restantes e idempotencia por {@code otpId}).
 */
@Component
@RequiredArgsConstructor
public class OtpValidatedListener {

    private final FirmaOtpService firmaOtpService;

    /**
     * @param event resultado de la validación
     */
    @KafkaListener(topics = "${topics.otp-validate-response}",
            groupId = "ms-negociador-orq",
            containerFactory = "otpKafkaListenerContainerFactory")
    public void handle(OtpValidatedEvent event) {
        firmaOtpService.procesarValidacion(event);
    }
}
