package com.negociadororq.infrastructure.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "topics")
public class TopicsProperties {


    private String otpRequest;
    private String otpValidate;
    private String otpValidateResponse;
    private String liquidacion;
    private String notificacionEmail;

}
