package com.negociadororq.infrastructure.config

import com.mongodb.MongoTimeoutException
import org.bson.Document
import org.springframework.boot.actuate.health.Status
import org.springframework.data.mongodb.core.MongoOperations
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** El health de Mongo responde en ~2 s aunque Mongo no conteste, sin acumular pings colgados. */
class MongoSaludAcotadaHealthIndicatorSpec extends Specification {

    CountDownLatch liberar = new CountDownLatch(1)

    def cleanup() {
        liberar.countDown()
    }

    def "con Mongo respondiendo el ping da UP"() {
        given:
        MongoOperations mongo = Stub() { executeCommand(_ as String) >> new Document("ok", 1) }
        def indicador = new MongoSaludAcotadaHealthIndicator(mongo)

        expect:
        indicador.health().status == Status.UP

        cleanup:
        indicador.destroy()
    }

    def "con Mongo sin responder da DOWN en ~2 s, sin esperar el timeout del driver"() {
        given:
        MongoOperations mongo = Stub() {
            executeCommand(_ as String) >> { liberar.await(30, TimeUnit.SECONDS); new Document("ok", 1) }
        }
        def indicador = new MongoSaludAcotadaHealthIndicator(mongo)

        when:
        long inicio = System.currentTimeMillis()
        def salud = indicador.health()
        long demora = System.currentTimeMillis() - inicio

        then:
        salud.status == Status.DOWN
        salud.details.motivo.toString().contains("sin respuesta")
        demora >= MongoSaludAcotadaHealthIndicator.TIMEOUT_PING_MS - 100
        demora < 4000

        cleanup:
        indicador.destroy()
    }

    def "con el ping anterior todavía colgado da DOWN al instante y no lanza otro"() {
        given:
        int pings = 0
        MongoOperations mongo = Stub() {
            executeCommand(_ as String) >> { pings++; liberar.await(30, TimeUnit.SECONDS); new Document("ok", 1) }
        }
        def indicador = new MongoSaludAcotadaHealthIndicator(mongo)
        indicador.health()

        when:
        long inicio = System.currentTimeMillis()
        def salud = indicador.health()

        then:
        salud.status == Status.DOWN
        salud.details.motivo == "ping anterior sin respuesta"
        System.currentTimeMillis() - inicio < 500
        pings == 1

        cleanup:
        indicador.destroy()
    }

    def "un error del driver da DOWN con el tipo de error, sin su mensaje"() {
        given:
        MongoOperations mongo = Stub() {
            executeCommand(_ as String) >> { throw new MongoTimeoutException("detalle interno") }
        }
        def indicador = new MongoSaludAcotadaHealthIndicator(mongo)

        when:
        def salud = indicador.health()

        then:
        salud.status == Status.DOWN
        salud.details.motivo == "MongoTimeoutException"
        !salud.details.toString().contains("detalle interno")

        cleanup:
        indicador.destroy()
    }

    def "se registra con el nombre del indicador mongo de Spring Boot, para reemplazarlo"() {
        expect:
        MongoSaludAcotadaHealthIndicator.getAnnotation(org.springframework.stereotype.Component).value() == "mongoHealthIndicator"
    }
}
