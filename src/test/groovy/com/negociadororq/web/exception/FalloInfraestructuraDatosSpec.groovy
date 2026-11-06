package com.negociadororq.web.exception

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLTimeoutException
import java.sql.SQLTransientConnectionException

import com.mongodb.MongoConnectionPoolClearedException
import com.mongodb.MongoServerUnavailableException
import com.mongodb.MongoSocketOpenException
import com.mongodb.MongoSocketReadTimeoutException
import com.mongodb.MongoTimeoutException
import com.mongodb.ServerAddress
import com.mongodb.connection.ClusterId
import com.mongodb.connection.ServerId
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.mongodb.UncategorizedMongoDbException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * Con el handler real resuelto por Spring: un fallo de infraestructura de datos (base caída o sin conexión,
 * timeout) responde 503 con el mensaje fijo, también cuando llega envuelto en otra excepción; los errores de datos
 * normales (integridad, resultado no único) siguen con la respuesta que tenían.
 */
class FalloInfraestructuraDatosSpec extends Specification {

    MockMvc mvc = MockMvcBuilders.standaloneSetup(new Lanzador())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()

    @Unroll
    def "fallo de infraestructura de datos (#caso) -> 503 con mensaje fijo"() {
        when:
        def respuesta = mvc.perform(get("/lanzar/" + caso)).andReturn().response

        then:
        respuesta.status == 503
        respuesta.getContentAsString(StandardCharsets.UTF_8).contains(FalloInfraestructuraDatos.MENSAJE)

        where:
        caso << ["recurso", "transaccion", "timeout", "conexion-envuelta", "conexion-no-transitoria", "timeout-jdbc",
                 "mongo-timeout", "mongo-socket", "mongo-envuelta", "mongo-pool", "mongo-no-disponible"]
    }

    @Unroll
    def "error de datos normal (#caso) -> #estado, sin el mensaje de infraestructura"() {
        when:
        def respuesta = mvc.perform(get("/lanzar/" + caso)).andReturn().response

        then:
        respuesta.status == estado
        !respuesta.getContentAsString(StandardCharsets.UTF_8).contains(FalloInfraestructuraDatos.MENSAJE)

        where:
        caso                 | estado
        "integridad"         | 500
        "resultado-no-unico" | 500
    }

    def "un error de datos de Mongo (clave duplicada) no se trata como caida de la base"() {
        when:
        def respuesta = mvc.perform(get("/lanzar/mongo-duplicado")).andReturn().response

        then:
        respuesta.status != 503
        !respuesta.getContentAsString(StandardCharsets.UTF_8).contains(FalloInfraestructuraDatos.MENSAJE)
    }

    def "el clasificador no confunde una excepcion sin causa de infraestructura"() {
        expect:
        !FalloInfraestructuraDatos.es(new RuntimeException("otra cosa", new IllegalArgumentException("x")))
        !FalloInfraestructuraDatos.es(null)
    }

    @RestController
    static class Lanzador {

        @GetMapping("/lanzar/{caso}")
        String lanzar(@PathVariable("caso") String caso) {
            switch (caso) {
                case "recurso":
                    throw new DataAccessResourceFailureException("sin conexion")
                case "transaccion":
                    throw new CannotCreateTransactionException("sin conexion")
                case "timeout":
                    throw new QueryTimeoutException("timeout")
                case "conexion-envuelta":
                    throw new RuntimeException("envuelta", new SQLTransientConnectionException("pool agotado"))
                case "conexion-no-transitoria":
                    throw new RuntimeException("envuelta", new SQLNonTransientConnectionException("enlace caido"))
                case "timeout-jdbc":
                    throw new RuntimeException("envuelta", new SQLTimeoutException("sin respuesta"))
                case "mongo-timeout":
                    throw new MongoTimeoutException("sin servidor disponible")
                case "mongo-socket":
                    throw new MongoSocketOpenException("no abre", new ServerAddress())
                case "mongo-envuelta":
                    throw new UncategorizedMongoDbException("envuelta",
                            new MongoSocketReadTimeoutException("lento", new ServerAddress(), new IOException("timeout")))
                case "mongo-pool":
                    throw new MongoConnectionPoolClearedException(new ServerId(new ClusterId(), new ServerAddress()), null)
                case "mongo-no-disponible":
                    throw new MongoServerUnavailableException("servidor cerrado")
                case "mongo-duplicado":
                    throw new DuplicateKeyException("clave duplicada")
                case "integridad":
                    throw new DataIntegrityViolationException("duplicado")
                case "resultado-no-unico":
                    throw new IncorrectResultSizeDataAccessException(1, 2)
                default:
                    return "ok"
            }
        }
    }
}
