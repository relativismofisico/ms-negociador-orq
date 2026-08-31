package com.negociadororq.infrastructure.kafka.consumer

import com.negociadororq.application.service.ReversionCuposService
import com.negociadororq.domain.event.LiquidacionFallidaEvent
import spock.lang.Specification

class LiquidacionFallidaConsumerSpec extends Specification {

    ReversionCuposService reversionCuposService = Mock()

    LiquidacionFallidaConsumer consumer = new LiquidacionFallidaConsumer(reversionCuposService)

    private LiquidacionFallidaEvent eventoBase() {
        def evento = new LiquidacionFallidaEvent()
        evento.numeroFactura = "FAC-001"
        evento.rutOperador = "operario1"
        evento.motivo = "cupo insuficiente"
        evento
    }

    def "consume el evento y delega la reversion al servicio"() {
        when:
        consumer.consumir(eventoBase())

        then:
        1 * reversionCuposService.revertirPorFactura("FAC-001", "operario1", "cupo insuficiente")
    }

    def "relanza la excepcion del servicio para que el error handler de Kafka la reintente/DLT-ee"() {
        given:
        reversionCuposService.revertirPorFactura(_, _, _) >> { throw new RuntimeException("fallo transitorio") }

        when:
        consumer.consumir(eventoBase())

        then:
        def ex = thrown(IllegalStateException)
        ex.cause.message == "fallo transitorio"
        ex.message.contains("FAC-001")
    }
}
