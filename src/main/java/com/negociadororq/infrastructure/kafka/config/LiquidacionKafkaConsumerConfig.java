package com.negociadororq.infrastructure.kafka.config;

import java.util.HashMap;
import java.util.Map;

import com.negociadororq.domain.event.LiquidacionFallidaEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configura el consumidor Kafka del topic de liquidación fallida, incluyendo reintentos con
 * backoff fijo y envío a dead-letter-topic cuando se agotan.
 */
@Configuration
public class LiquidacionKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${kafka.consumer.retry.backoff-ms:2000}")
    private long retryBackoffMs;

    @Bean
    public ConsumerFactory<String, LiquidacionFallidaEvent>
    liquidacionConsumerFactory() {

        JsonDeserializer<LiquidacionFallidaEvent> deserializer =
                new JsonDeserializer<>(LiquidacionFallidaEvent.class);

        deserializer.addTrustedPackages("*");

        deserializer.setUseTypeHeaders(false);

        Map<String, Object> props = new HashMap<>();

        props.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        props.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "negociador-group"
        );

        props.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        props.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public DefaultErrorHandler liquidacionErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoffMs, retryMaxAttempts)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LiquidacionFallidaEvent>
    liquidacionKafkaListenerContainerFactory(
            DefaultErrorHandler liquidacionErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, LiquidacionFallidaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(
                liquidacionConsumerFactory()
        );

        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.RECORD
        );

        factory.setCommonErrorHandler(liquidacionErrorHandler);

        return factory;
    }
}

