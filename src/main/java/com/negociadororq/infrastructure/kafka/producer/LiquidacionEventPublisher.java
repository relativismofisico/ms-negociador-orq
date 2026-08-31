package com.negociadororq.infrastructure.kafka.producer;

import java.util.List;

import com.negociadororq.application.dto.OperacionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica el evento de liquidación de un conjunto de operaciones al topic correspondiente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiquidacionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${topics.liquidacion}")
    private String topic;

    /**
     * @param operaciones operaciones a incluir en el evento de liquidación
     */
    public void publicarLiquidacion(List<OperacionRequest> operaciones) {

        log.info("Publicando evento de liquidación para {} facturas",
                operaciones.size());

        kafkaTemplate.send(topic, operaciones);
    }
}

