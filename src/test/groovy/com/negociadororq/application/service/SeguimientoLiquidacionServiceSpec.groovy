package com.negociadororq.application.service

import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.application.port.output.CancelacionLiquidacionOutputPort
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.OperacionPendienteOutputPort
import com.negociadororq.application.port.output.OutboxOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.ReferenciaFactura
import com.negociadororq.domain.model.TipoOperacionPendiente
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime

/** Fase 28d (A4): seguimiento de la solicitud de liquidación: reenvíos, cancelación atómica y revisión manual. */
class SeguimientoLiquidacionServiceSpec extends Specification {

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 18, 0)
    static final String CLAVE = "neg-1|CUFE-1"
    static final String DATOS = '{"v":1,"numeroFactura":"F-1","idEvento":"liquidacion.solicitada|neg-1|CUFE-1"}'

    OperacionPendienteOutputPort operaciones = Mock()
    OutboxOutputPort outbox = Mock()
    FacturaOutputPort facturaPort = Mock()
    FacturaPatchOutputPort facturaPatch = Mock()
    CancelacionLiquidacionOutputPort cancelacion = Mock()
    ReversionCuposService reversionCupos = Mock()
    ReversionExactaService reversionExacta = Mock()
    ClockPort clock = Stub() { now() >> AHORA }
    SeguimientoLiquidacionService servicio = new SeguimientoLiquidacionService(operaciones, outbox, facturaPort,
            facturaPatch, cancelacion, reversionCupos, reversionExacta, clock,
            new SeguimientoLiquidacionService.Parametros(Duration.ofMinutes(15), 4, 8, 50))

    static OperacionPendiente op(int intentos, TipoOperacionPendiente tipo = TipoOperacionPendiente.LIQUIDACION_PENDIENTE) {
        new OperacionPendiente(9L, tipo, CLAVE, SeguimientoLiquidacionService.SOLICITADA, DATOS, null, intentos,
                AHORA.minusHours(1), AHORA.minusHours(1), null)
    }

    static Factura factura(String estadoNegociacion = "APROBADA") {
        new Factura().tap { cufe = "CUFE-1"; rutProveedor = "PROV"; rutPagador = "PAG"; it.estadoNegociacion = estadoNegociacion }
    }

    void tomar(OperacionPendiente o) {
        1 * operaciones.tomarParaReanudar([SeguimientoLiquidacionService.SOLICITADA], AHORA.minusMinutes(15), AHORA,
                AHORA.plusMinutes(7).plusSeconds(30), 50) >> [o]
    }

    def "registrar la solicitud: LIQUIDACION_PENDIENTE en LIQ_SOLICITADA con el número y el id del evento"() {
        when:
        servicio.registrarSolicitud("neg-1", "CUFE-1", "F-1", "liquidacion.solicitada|neg-1|CUFE-1")

        then:
        1 * operaciones.registrar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE, "LIQ_SOLICITADA", DATOS) >> true
    }

    def "la factura ya está LIQUIDADA: COMPLETADA, sin reenviar, cancelar ni revertir"() {
        given:
        facturaPort.findByCufe("CUFE-1") >> Optional.of(factura("LIQUIDADA"))

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(3)) }
        1 * operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE, "LIQ_SOLICITADA", "COMPLETADA") >> true
        0 * outbox._
        0 * cancelacion._
        0 * reversionCupos._
        0 * reversionExacta._
    }

    def "la creación ya se revirtió por otro camino (fallida consumida, rechazo): REVERTIDA sin tocar nada más"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(TipoOperacionPendiente.CREAR_NEGOCIACION, CLAVE) >> Optional.of(new OperacionPendiente(1L,
                TipoOperacionPendiente.CREAR_NEGOCIACION, CLAVE, "REVERTIDA", "{}", null, 0, AHORA, AHORA, null))

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(6)) }
        1 * operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE, "LIQ_SOLICITADA", "REVERTIDA") >> true
        0 * cancelacion._
        0 * reversionExacta._
    }

    def "intento #intento de 4: reencola el mismo evento del outbox; no cancela"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(intento)) }
        1 * outbox.reencolar("liquidacion.solicitada|neg-1|CUFE-1") >> true
        0 * cancelacion._
        0 * operaciones.transicionar(*_)

        where:
        intento << [1, 2, 3, 4]
    }

    def "agotados los reenvíos, cancela: #resultado"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(5)) }
        0 * outbox._
        1 * cancelacion.cancelar("neg-1", "CUFE-1") >> resultado
        fallida * reversionCupos.revertirPorFactura("neg-1", "CUFE-1", "F-1", "ms-negociador-orq", _)
        cancelada * reversionExacta.revertir(TipoOperacionPendiente.LIQUIDACION_CANCELADA, "neg-1", "CUFE-1", _)
        cancelada * facturaPatch.actualizarEstadoFirmaPorCufe(new ReferenciaFactura("CUFE-1", "PROV", "PAG"), EstadoFirma.RECHAZADA)
        1 * operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE, "LIQ_SOLICITADA", hacia) >> true

        where:
        resultado                                          | fallida | cancelada | hacia
        CancelacionLiquidacionOutputPort.Resultado.LIQUIDADA | 0       | 0         | "COMPLETADA"
        CancelacionLiquidacionOutputPort.Resultado.FALLIDA   | 1       | 0         | "REVERTIDA"
        CancelacionLiquidacionOutputPort.Resultado.CANCELADA | 0       | 1         | "REVERTIDA"
    }

    def "liquidador no disponible al cancelar: deja el error y se reintenta en el siguiente ciclo; nada se revierte"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(intento)) }
        1 * cancelacion.cancelar(*_) >> { throw new ServicioDependienteNoDisponibleException("caído") }
        1 * operaciones.registrarError(9L, _)
        0 * operaciones.transicionar(*_)
        0 * reversionCupos._
        0 * reversionExacta._

        where:
        intento << [5, 12]
    }

    def "agotados reenvíos y cancelaciones: REVISION_MANUAL, contador de atascadas y SIN correo; los cupos se retienen"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()
        def antes = ReconciliadorOperacionesService.atascadas()

        when:
        servicio.seguir()

        then:
        interaction { tomar(op(13)) }
        1 * operaciones.transicionar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE, "LIQ_SOLICITADA", "REVISION_MANUAL") >> true
        0 * cancelacion._
        0 * reversionCupos._
        0 * reversionExacta._
        0 * outbox._
        ReconciliadorOperacionesService.atascadas() == antes + 1
    }

    def "sin datos o con otro formato no hay evento que reencolar: avisa y no falla"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()
        def sinDatos = new OperacionPendiente(9L, TipoOperacionPendiente.LIQUIDACION_PENDIENTE, CLAVE,
                SeguimientoLiquidacionService.SOLICITADA, datos, null, 1, AHORA.minusHours(1), AHORA.minusHours(1), null)

        when:
        def n = servicio.seguir()

        then:
        1 * operaciones.tomarParaReanudar(*_) >> [sinDatos]
        0 * outbox.reencolar(_)
        0 * cancelacion._
        n == 1

        where:
        datos << [null, '{"otro":"formato"}']
    }

    def "el evento ya no está en el outbox: el reenvío avisa y no falla"() {
        given:
        facturaPort.findByCufe(_) >> Optional.of(factura())
        operaciones.buscar(*_) >> Optional.empty()

        when:
        servicio.seguir()

        then:
        1 * operaciones.tomarParaReanudar(*_) >> [op(2)]
        1 * outbox.reencolar(_) >> false
        0 * cancelacion._
    }

    def "un error inesperado se registra y no corta el ciclo; otros tipos se ignoran"() {
        given:
        facturaPort.findByCufe(_) >> { throw new IllegalStateException("Mongo caído") }

        when:
        def n = servicio.seguir()

        then:
        1 * operaciones.tomarParaReanudar(*_) >> [op(1), op(1, TipoOperacionPendiente.RECHAZO)]
        1 * operaciones.registrarError(9L, { it.contains("IllegalStateException") })
        n == 0
    }
}
