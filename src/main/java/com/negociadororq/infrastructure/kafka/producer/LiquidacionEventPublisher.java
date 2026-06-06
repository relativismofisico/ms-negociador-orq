package com.negociadororq.infrastructure.kafka.producer;

import com.negociadororq.application.dto.OperacionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiquidacionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${topics.liquidacion}")
    private String topic;

    public void publicarLiquidacion(List<OperacionRequest> operaciones) {

        log.info("Publicando evento de liquidación para {} facturas",
                operaciones.size());

        kafkaTemplate.send(topic, operaciones);
    }
}

