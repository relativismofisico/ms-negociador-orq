package com.negociadororq.infrastructure.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de los topics Kafka usados por el servicio, configurables por el prefijo {@code topics}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "topics")
public class TopicsProperties {


    private String otpRequest;
    private String otpValidate;
    private String otpValidateResponse;
    private String liquidacion;
    private String notificacionEmail;
    private String liquidacionFallida;

}

