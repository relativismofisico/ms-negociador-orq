package com.negociadororq.infrastructure.kafka.consumer

import com.factoring.commons.outbox.idempotencia.ConsumoIdempotente
import com.negociadororq.application.service.PropagacionCupoOfertaService
import com.negociadororq.domain.event.CupoOfertaIndirectaCambiadaEvent
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

/** Fase 28d (P-19): el consumidor aplica cada cambio una sola vez por (oferta, versión). */
class CupoOfertaIndirectaConsumerSpec extends Specification {

    PropagacionCupoOfertaService propagacion = Mock()
    ConsumoIdempotente idempotente = Mock()
    ObjectProvider<ConsumoIdempotente> proveedor = Stub() { getIfAvailable() >> idempotente }
    CupoOfertaIndirectaConsumer consumidor = new CupoOfertaIndirectaConsumer(propagacion, proveedor)
    def evento = new CupoOfertaIndirectaCambiadaEvent(7L, 3L, new BigDecimal("10000.00"), new BigDecimal("12000.00"))

    def "aplica dentro de ConsumoIdempotente con la clave natural de oferta y versión"() {
        when:
        consumidor.consumir(evento)

        then:
        1 * idempotente.procesar("negociador.cupo-oferta-indirecta", "cupo-oferta|7|3", _ as Runnable) >> { c, k, Runnable r ->
            r.run(); true
        }
        1 * propagacion.aplicar(7L, 3L, new BigDecimal("12000.00"))
    }

    def "un duplicado (ya marcado) no vuelve a aplicar"() {
        when:
        consumidor.consumir(evento)

        then:
        1 * idempotente.procesar(_, _, _) >> false
        0 * propagacion._
    }

    def "sin almacén de marcas aplica igual (la versión de cada fila descarta repetidos)"() {
        given:
        def sinAlmacen = new CupoOfertaIndirectaConsumer(propagacion, Stub(ObjectProvider) { getIfAvailable() >> null })

        when:
        sinAlmacen.consumir(evento)

        then:
        1 * propagacion.aplicar(7L, 3L, new BigDecimal("12000.00"))
    }
}
