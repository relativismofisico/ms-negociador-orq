package com.negociadororq.infrastructure.alerta

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

/** Alertas de la firma por OTP por log (Fase 28c): etiquetas fijas y contadores. */
class AlertaFirmaLogAdapterSpec extends Specification {

    Logger logger = (Logger) LoggerFactory.getLogger(AlertaFirmaLogAdapter)
    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    AlertaFirmaLogAdapter adapter = new AlertaFirmaLogAdapter()

    def setup() {
        appender.start()
        logger.addAppender(appender)
    }

    def cleanup() {
        logger.detachAppender(appender)
    }

    def "endosoDuplicado deja [ENDOSO-DUPLICADO] con la negociación y el contador, separado de las firmas fallidas"() {
        when:
        adapter.endosoDuplicado("NEG-1")
        adapter.endosoDuplicado("NEG-2")
        adapter.firmaFallida()

        then:
        adapter.endososDuplicados() == 2
        adapter.total() == 1
        def mensajes = appender.list*.formattedMessage
        mensajes.count { it.startsWith("[ENDOSO-DUPLICADO] negociación NEG-1:") } == 1
        mensajes.count { it.startsWith("[ENDOSO-DUPLICADO] negociación NEG-2:") && it.endsWith("desde el arranque: 2") } == 1
        mensajes.count { it.startsWith("[ALERTA][FIRMA-FALLIDA]") && it.endsWith(": 1") } == 1
        mensajes.every { !it.contains("123456") }
    }
}
