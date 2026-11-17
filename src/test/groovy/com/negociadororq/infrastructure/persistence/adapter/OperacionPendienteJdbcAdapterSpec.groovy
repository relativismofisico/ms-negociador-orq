package com.negociadororq.infrastructure.persistence.adapter

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.TipoOperacionPendiente
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.MySQLContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * Fase 28d, opción A: la tabla única operacion_pendiente con MySQL 8 real y la migración V3 del servicio. Alta
 * idempotente por (tipo, clave negociacion|cufe), transiciones CAS y toma con concesión para el reconciliador.
 */
class OperacionPendienteJdbcAdapterSpec extends Specification {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 17, 10, 0)

    @Shared
    MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCreateContainerCmdModifier { it.withName("integrity-e2e-tc-mysql-" + UUID.randomUUID().toString().substring(0, 8)) }
    @Shared
    JdbcTemplate jdbc
    @Shared
    NamedParameterJdbcTemplate conNombres

    LocalDateTime ahora = T0
    ClockPort clock = { ahora } as ClockPort
    OperacionPendienteJdbcAdapter adapter

    def setupSpec() {
        mysql.start()
        def ds = new DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        // Solo la migración de esta tabla: V1 es la línea base de una base compartida y referencia tablas de otros
        // servicios.
        ScriptUtils.executeSqlScript(ds.connection, new ClassPathResource("db/migration/V3__operacion_pendiente.sql"))
        jdbc = new JdbcTemplate(ds)
        conNombres = new NamedParameterJdbcTemplate(ds)
    }

    def cleanupSpec() {
        mysql.stop()
    }

    def setup() {
        jdbc.update("DELETE FROM operacion_pendiente")
        adapter = new OperacionPendienteJdbcAdapter(jdbc, conNombres, clock)
    }

    static final String CLAVE = OperacionPendiente.clave("NEG-1", "CUFE-A")

    def "la clave natural es negociacion|cufe y rechaza partes vacías o con el separador"() {
        expect:
        CLAVE == "NEG-1|CUFE-A"
        OperacionPendiente.clave("NEG-1") == "NEG-1"

        when:
        OperacionPendiente.clave("NEG-1", parte)

        then:
        thrown(IllegalArgumentException)

        where:
        parte << [null, " ", "A|B"]
    }

    def "alta idempotente: la misma operación dos veces deja una fila; otro CUFE de la misma negociación es otra"() {
        expect:
        adapter.registrar(TipoOperacionPendiente.RECHAZO, CLAVE, "INICIADA", '{"x":1}')
        !adapter.registrar(TipoOperacionPendiente.RECHAZO, CLAVE, "INICIADA", '{"x":2}')
        adapter.registrar(TipoOperacionPendiente.RECHAZO, OperacionPendiente.clave("NEG-1", "CUFE-B"), "INICIADA", null)
        adapter.registrar(TipoOperacionPendiente.VENCIMIENTO, CLAVE, "INICIADA", null)
        jdbc.queryForObject("SELECT COUNT(*) FROM operacion_pendiente", Long) == 3
        adapter.buscar(TipoOperacionPendiente.RECHAZO, CLAVE).get().datos() == '{"x":1}'
    }

    def "transición CAS: solo desde el estado esperado; de dos transiciones concurrentes gana una"() {
        given:
        adapter.registrar(TipoOperacionPendiente.RECHAZO, CLAVE, "INICIADA", null)
        def largada = new CountDownLatch(1)
        def ganadas = new CopyOnWriteArrayList<Boolean>()

        when:
        def hilos = (1..4).collect {
            Thread.start {
                largada.await()
                ganadas << adapter.transicionar(TipoOperacionPendiente.RECHAZO, CLAVE, "INICIADA", "REVERTIDO_MYSQL")
            }
        }
        largada.countDown()
        hilos*.join(30_000)

        then:
        ganadas.count { it } == 1
        !adapter.transicionar(TipoOperacionPendiente.RECHAZO, CLAVE, "INICIADA", "COMPLETADA")
        adapter.buscar(TipoOperacionPendiente.RECHAZO, CLAVE).get().estado() == "REVERTIDO_MYSQL"
    }

    def "reconciliador: respeta la gracia y la concesión, suma intentos y dos tomas no se pisan"() {
        given: "dos operaciones viejas y una reciente, en estado reanudable"
        ahora = T0.minusMinutes(10)
        adapter.registrar(TipoOperacionPendiente.RECHAZO, CLAVE, "REVERTIDO_MYSQL", null)
        adapter.registrar(TipoOperacionPendiente.VENCIMIENTO, CLAVE, "REVERTIDO_MYSQL", null)
        ahora = T0
        adapter.registrar(TipoOperacionPendiente.RECHAZO, OperacionPendiente.clave("NEG-2", "CUFE-C"), "REVERTIDO_MYSQL", null)

        when:
        def primera = adapter.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.minusMinutes(2), T0, T0.plusMinutes(5), 10)
        def segunda = adapter.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.minusMinutes(2), T0, T0.plusMinutes(5), 10)

        then: "solo las viejas, con concesión e intento; la segunda toma no encuentra nada"
        primera.size() == 2
        primera.every { it.intentos() == 1 && it.leaseHasta() == T0.plusMinutes(5) }
        segunda.isEmpty()

        when: "vencida la concesión, se vuelven a tomar; un error libera la concesión"
        adapter.registrarError(primera[0].id(), "X" * 600)
        def tercera = adapter.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.minusMinutes(2), T0.plusMinutes(1),
                T0.plusMinutes(6), 10)

        then:
        tercera*.id() == [primera[0].id()]
        tercera[0].intentos() == 2
        tercera[0].ultimoError().length() == 500
    }

    def "una transición libera la concesión y saca la operación de los estados reanudables"() {
        given:
        ahora = T0.minusMinutes(10)
        adapter.registrar(TipoOperacionPendiente.RECHAZO, CLAVE, "REVERTIDO_MYSQL", null)
        ahora = T0
        adapter.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.minusMinutes(2), T0, T0.plusMinutes(5), 10)

        when:
        def ok = adapter.transicionar(TipoOperacionPendiente.RECHAZO, CLAVE, "REVERTIDO_MYSQL", "COMPLETADA")

        then:
        ok
        adapter.buscar(TipoOperacionPendiente.RECHAZO, CLAVE).get().leaseHasta() == null
        adapter.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.plusMinutes(10), T0.plusMinutes(10), T0.plusMinutes(15), 10).isEmpty()
    }
}
