package com.negociadororq

import com.mongodb.MongoClientSettings
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.MILLISECONDS

/** Mongo caído responde en ~10 s (no 30 s): selección de servidor y conexión acotadas, lectura sin tocar. */
class TiemposMongoConfigSpec extends Specification {

    private static MongoClientSettings aplicar(ApplicationContextRunner runner) {
        MongoClientSettings resultado = null
        runner.withUserConfiguration(TiemposMongoConfig).run { ctx ->
            def builder = MongoClientSettings.builder()
            ctx.getBean(MongoClientSettingsBuilderCustomizer).customize(builder)
            resultado = builder.build()
        }
        resultado
    }

    def "por defecto: selección de servidor 10 s y conexión 5 s; el timeout de lectura del socket no cambia"() {
        when:
        def ajustes = aplicar(new ApplicationContextRunner())

        then:
        ajustes.clusterSettings.getServerSelectionTimeout(MILLISECONDS) == 10_000
        ajustes.socketSettings.getConnectTimeout(MILLISECONDS) == 5_000
        ajustes.socketSettings.getReadTimeout(MILLISECONDS) == MongoClientSettings.builder().build().socketSettings
                .getReadTimeout(MILLISECONDS)
    }

    def "se configuran con MONGO_SERVER_SELECTION_TIMEOUT_MS y MONGO_CONNECT_TIMEOUT_MS"() {
        when:
        def ajustes = aplicar(new ApplicationContextRunner().withPropertyValues(
                "MONGO_SERVER_SELECTION_TIMEOUT_MS=20000", "MONGO_CONNECT_TIMEOUT_MS=3000"))

        then:
        ajustes.clusterSettings.getServerSelectionTimeout(MILLISECONDS) == 20_000
        ajustes.socketSettings.getConnectTimeout(MILLISECONDS) == 3_000
    }

    def "se aplica después del customizer estándar de Spring Boot: manda aunque la URI traiga otro valor"() {
        expect:
        TiemposMongoConfig.getDeclaredMethod("tiemposMongo", long, int).getAnnotation(Order).value() == Ordered.LOWEST_PRECEDENCE
    }
}
