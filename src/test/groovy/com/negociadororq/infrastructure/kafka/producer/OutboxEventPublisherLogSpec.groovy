package com.negociadororq.infrastructure.kafka.producer

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.negociadororq.domain.event.DestinatarioEmail
import com.negociadororq.domain.event.NotificacionEmailEvent
import com.negociadororq.infrastructure.kafka.config.TopicsProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

/**
 * El publicador del outbox registra el tipo de evento y el topic, nunca el evento completo: un
 * NotificacionEmailEvent lleva destinatarios y data del correo.
 */
class OutboxEventPublisherLogSpec extends Specification {

    def "publicar registra tipo y topic, sin destinatarios ni data"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(OutboxEventPublisher)
        logger.level = Level.DEBUG
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        KafkaTemplate<String, Object> template = Mock()
        def topics = new TopicsProperties()
        topics.notificacionEmail = "notificacion.email"
        def publisher = new OutboxEventPublisher(template, topics, Stub(ObjectProvider) { getIfAvailable() >> null })
        publisher.init()
        def event = NotificacionEmailEvent.builder()
                .idEvento("EVT-1").idNegociacion("NEG-1").tipoNotificacion("FIRMA_ENDOSO")
                .destinatarios([new DestinatarioEmail("PROVEEDOR", "800222", "EMPRESA")])
                .data([enlace: "https://portal.test/endoso?token=abc123secreto"])
                .build()

        when:
        publisher.publicar(event)

        then:
        1 * template.send("notificacion.email", event)
        def mensajes = appender.list*.formattedMessage
        mensajes.any { it.contains("NotificacionEmailEvent") && it.contains("notificacion.email") }
        mensajes.every { !it.contains("abc123secreto") && !it.contains("800222") }

        cleanup:
        logger.detachAppender(appender)
    }
}
