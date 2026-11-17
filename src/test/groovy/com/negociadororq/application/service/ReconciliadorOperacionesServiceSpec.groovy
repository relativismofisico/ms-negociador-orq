package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.OperacionPendienteOutputPort
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.TipoOperacionPendiente
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime

/** Fase 28d, opción A: el reconciliador único reparte las operaciones tomadas entre los reanudadores de cada tipo. */
class ReconciliadorOperacionesServiceSpec extends Specification {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 17, 10, 0)

    OperacionPendienteOutputPort operaciones = Mock()
    ClockPort clock = Stub() { now() >> T0 }

    static OperacionPendiente op(long id, TipoOperacionPendiente tipo, String estado) {
        new OperacionPendiente(id, tipo, "NEG-1|CUFE-" + id, estado, null, null, 1, T0, T0, null)
    }

    ReanudadorOperacion reanudador(TipoOperacionPendiente tipo, List<String> estados, Closure efecto) {
        [tipo: { tipo }, estadosReanudables: { estados }, reanudar: efecto] as ReanudadorOperacion
    }

    def "sin reanudadores no consulta nada"() {
        when:
        def n = new ReconciliadorOperacionesService(operaciones, clock, [], Duration.ofMinutes(2),
                Duration.ofMinutes(5), 50).reconciliar()

        then:
        n == 0
        0 * operaciones._
    }

    def "toma con gracia y concesión, reanuda cada una con el reanudador de su tipo y deja el error de la que falla"() {
        given:
        def rechazos = []
        def vencimientos = []
        def servicio = new ReconciliadorOperacionesService(operaciones, clock, [
                reanudador(TipoOperacionPendiente.RECHAZO, ["REVERTIDO_MYSQL"], { rechazos << it }),
                reanudador(TipoOperacionPendiente.VENCIMIENTO, ["REVERTIDO_MYSQL"],
                        { vencimientos << it; throw new IllegalStateException("Mongo caído") })],
                Duration.ofMinutes(2), Duration.ofMinutes(5), 50)

        when:
        def n = servicio.reconciliar()

        then:
        1 * operaciones.tomarParaReanudar(["REVERTIDO_MYSQL"], T0.minusMinutes(2), T0, T0.plusMinutes(5), 50) >> [
                op(1, TipoOperacionPendiente.RECHAZO, "REVERTIDO_MYSQL"),
                op(2, TipoOperacionPendiente.VENCIMIENTO, "REVERTIDO_MYSQL"),
                op(3, TipoOperacionPendiente.RECHAZO, "OTRO_ESTADO")]
        1 * operaciones.registrarError(2L, "IllegalStateException: Mongo caído")
        n == 1
        rechazos*.id() == [1L]
        vencimientos*.id() == [2L]
    }
}
