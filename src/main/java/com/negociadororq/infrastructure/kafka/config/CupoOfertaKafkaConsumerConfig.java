package com.negociadororq.infrastructure.kafka.config;

import java.util.HashMap;
import java.util.Map;

import com.negociadororq.domain.event.CupoOfertaIndirectaCambiadaEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Fase 28d (P-19): consumidor del cambio de cupo por proveedor de la oferta indirecta (topic
 * {@code cupoOfertaIndirectaCambiadaTopic}). Tipo fijo (sin la cabecera de tipo de registro), ack por registro y el mismo
 * manejo de errores que la liquidación fallida (reintentos con backoff fijo y luego DLT).
 */
@Configuration
public class CupoOfertaKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /** @return fábrica de consumidores del evento */
    @Bean
    public ConsumerFactory<String, CupoOfertaIndirectaCambiadaEvent> cupoOfertaConsumerFactory() {
        JsonDeserializer<CupoOfertaIndirectaCambiadaEvent> deserializer =
                new JsonDeserializer<>(CupoOfertaIndirectaCambiadaEvent.class);
        deserializer.addTrustedPackages(CupoOfertaIndirectaCambiadaEvent.class.getPackageName());
        deserializer.setUseTypeHeaders(false);
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "negociador-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * @param liquidacionErrorHandler reintentos y DLT (el de {@link LiquidacionKafkaConsumerConfig})
     * @return fábrica de contenedores del listener
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CupoOfertaIndirectaCambiadaEvent>
            cupoOfertaKafkaListenerContainerFactory(DefaultErrorHandler liquidacionErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CupoOfertaIndirectaCambiadaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cupoOfertaConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(liquidacionErrorHandler);
        return factory;
    }
}
