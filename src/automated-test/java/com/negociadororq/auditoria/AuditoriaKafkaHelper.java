package com.negociadororq.auditoria;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Lee el topic de auditoría ({@code auditoria.accion}) para la suite Karate: verifica que una acción del servicio
 * realmente salió a Kafka, con la clave por entidad que exige el contrato.
 */
public final class AuditoriaKafkaHelper {

    private AuditoriaKafkaHelper() {
    }

    /**
     * Busca, desde el principio del topic, el primer evento con esa clave y esa acción.
     *
     * @param bootstrap servidores Kafka
     * @param topic     topic de auditoría
     * @param clave     clave Kafka ({@code <entidad.tipo>:<entidad.id>})
     * @param accion    acción esperada (p. ej. {@code EMPRESA_REGISTRADA})
     * @param timeoutMs tiempo máximo de espera
     * @return el JSON del evento, o {@code null} si no apareció a tiempo
     */
    public static String buscar(String bootstrap, String topic, String clave, String accion, long timeoutMs) {
        return primero(bootstrap, topic, accion, timeoutMs, registro -> clave.equals(registro.key()));
    }

    /**
     * Busca el primer evento de esa acción cuyo JSON contenga el texto (para entidades cuyo id no conoce la suite,
     * p. ej. una relación comercial recién creada, que se ubica por el NIT del proveedor).
     *
     * @return el JSON del evento, o {@code null} si no apareció a tiempo
     */
    public static String buscarConTexto(String bootstrap, String topic, String accion, String texto, long timeoutMs) {
        return primero(bootstrap, topic, accion, timeoutMs, registro -> registro.value().contains(texto));
    }

    private static String primero(String bootstrap, String topic, String accion, long timeoutMs,
                                  java.util.function.Predicate<ConsumerRecord<String, String>> filtro) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "karate-negociador-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        String marcaAccion = "\"accion\":\"" + accion + "\"";
        long limite = System.currentTimeMillis() + timeoutMs;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> registro : consumer.poll(Duration.ofMillis(500))) {
                    if (registro.value() != null && registro.value().contains(marcaAccion) && filtro.test(registro)) {
                        return registro.value();
                    }
                }
            }
        }
        return null;
    }
}
