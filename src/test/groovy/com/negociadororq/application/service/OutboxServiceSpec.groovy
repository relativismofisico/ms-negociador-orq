package com.negociadororq.application.service

import com.negociadororq.application.port.output.OutboxOutputPort
import spock.lang.Specification

class OutboxServiceSpec extends Specification {

    OutboxOutputPort outboxPort = Mock()
    OutboxService service = new OutboxService(outboxPort)

    def "guardarEvento delega en outboxPort"() {
        given:
        def aggregateId = "neg-001"
        def tipoEvento = "NOTIFICACION_EMAIL"
        def evento = new Object()

        when:
        service.guardarEvento(aggregateId, tipoEvento, evento)

        then:
        1 * outboxPort.guardarEvento(aggregateId, tipoEvento, evento)
    }

    def "guardarEvento con aggregateId null delega correctamente"() {
        when:
        service.guardarEvento(null, "TIPO", "payload")

        then:
        1 * outboxPort.guardarEvento(null, "TIPO", "payload")
    }

    def "guardarEvento con objeto complejo delega correctamente"() {
        given:
        def payload = [key: "value", numero: 42]

        when:
        service.guardarEvento("agg-100", "EVENTO_COMPLEJO", payload)

        then:
        1 * outboxPort.guardarEvento("agg-100", "EVENTO_COMPLEJO", payload)
    }
}
