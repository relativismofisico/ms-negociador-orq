package com.negociadororq.infrastructure.kafka.producer

import com.negociadororq.domain.event.NotificacionEmailEvent
import com.negociadororq.infrastructure.kafka.config.TopicsProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

/** El outbox solo marca enviado lo que Kafka confirmó: publicar espera el ack, con un tope. */
class OutboxEventPublisherAckSpec extends Specification {

    KafkaTemplate<String, Object> template = Mock()
    OutboxEventPublisher publisher

    def setup() {
        def topics = new TopicsProperties()
        topics.notificacionEmail = "notificacion.email"
        publisher = new OutboxEventPublisher(template, topics, Stub(ObjectProvider))
        publisher.init()
        publisher.esperaAck = Duration.ofMillis(200)
    }

    def "con el ack del broker publicar termina sin error"() {
        when:
        publisher.publicar(new NotificacionEmailEvent())

        then:
        1 * template.send("notificacion.email", _) >> CompletableFuture.completedFuture(null)
        noExceptionThrown()
    }

    def "si el broker no confirma a tiempo, publicar falla en el tope (el scheduler deja el evento pendiente)"() {
        given:
        template.send(_, _) >> new CompletableFuture()

        when:
        long inicio = System.currentTimeMillis()
        publisher.publicar(new NotificacionEmailEvent())

        then:
        def e = thrown(IllegalStateException)
        e.cause instanceof TimeoutException
        System.currentTimeMillis() - inicio < 3000
    }

    def "si el envío falla, publicar falla"() {
        given:
        template.send(_, _) >> CompletableFuture.failedFuture(new RuntimeException("broker caído"))

        when:
        publisher.publicar(new NotificacionEmailEvent())

        then:
        thrown(IllegalStateException)
    }

    def "interrumpido mientras espera: restaura la interrupción y falla"() {
        given:
        template.send(_, _) >> new CompletableFuture()
        publisher.esperaAck = Duration.ofSeconds(5)
        Thread.currentThread().interrupt()

        when:
        publisher.publicar(new NotificacionEmailEvent())

        then:
        thrown(IllegalStateException)
        Thread.interrupted()
    }
}
