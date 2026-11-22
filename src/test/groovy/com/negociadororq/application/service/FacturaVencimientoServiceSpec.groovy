package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.OperacionPendienteOutputPort
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.TipoOperacionPendiente
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fase 28d (A5): un caso por fila de la tabla de estados aprobada por John (vence y revierte, vence sin revertir, no
 * toca), el compare-and-set perdido, la reanudación tras una caída y la consulta por fecha.
 */
class FacturaVencimientoServiceSpec extends Specification {

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 21, 2, 0)

    FacturaOutputPort facturaPort = Mock()
    FacturaPatchOutputPort facturaPatch = Mock()
    OperacionPendienteOutputPort operaciones = Mock()
    ReversionExactaService reversionExacta = Mock()
    ClockPort clock = Stub() {
        now() >> AHORA
    }
    FacturaVencimientoService service = new FacturaVencimientoService(
            facturaPort, facturaPatch, operaciones, reversionExacta, clock)

    static Factura factura(String estado, String firma, String negociacionId, int diasParaVencer = 5) {
        def f = new Factura()
        f.id = "id-" + estado + "-" + firma
        f.numeroFactura = "F-" + estado
        f.cufe = "CUFE-" + estado + "-" + firma
        f.estadoNegociacion = estado
        f.estadoFirma = firma
        f.negociacionId = negociacionId
        f.fechavencimientoFactura = AHORA.toLocalDate().plusDays(diasParaVencer)
        f
    }

    def setup() {
        facturaPort.findVencidasConReversionPendiente() >> []
    }

    @Unroll
    def "fila #fila (#estado + #firma): vence y revierte los cupos exactos una sola vez"() {
        given:
        def f = factura(estado, firma, "NEG-1")
        facturaPort.findCandidatasVencimiento(AHORA.toLocalDate().plusDays(10),
                FacturaVencimientoService.ESTADOS_CANDIDATOS) >> [f]
        operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, "NEG-1|" + f.cufe) >> Optional.empty()

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        1 * facturaPatch.marcarVencidaSiCoincide(f.id, estado, firma, true) >> true
        1 * reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO, "NEG-1", f.cufe, _) >>
                ReversionExactaService.Resultado.REVERTIDA
        1 * facturaPatch.quitarReversionPendiente(f.id)
        r.vencidas() == 1
        r.revertidas() == 1

        where:
        fila | estado         | firma
        1    | "NEGOCIADA"    | "SIN_CESION"
        1    | "NEGOCIADA"    | "SIN_DESCUENTO"
        2    | "ENDOSADA"     | "EN_REVISION"
        3    | "NEGOCIADA"    | "EN_APROBACION"
        4    | "SIN_NEGOCIAR" | "EN_REVISION"
        4    | "SIN_NEGOCIAR" | "EN_APROBACION"
    }

    @Unroll
    def "fila 5 (#estado, negociación #negociacionId): vence sin cupos que devolver"() {
        given:
        def f = factura(estado, firma, negociacionId)
        facturaPort.findCandidatasVencimiento(_, _) >> [f]
        operaciones.buscar(_, _) >> Optional.empty()

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        1 * facturaPatch.marcarVencidaSiCoincide(f.id, estado, firma, false) >> true
        0 * reversionExacta._
        r.vencidas() == 1
        r.revertidas() == 0

        where:
        estado         | firma         | negociacionId
        "CARGADA"      | "SIN_FIRMA"   | null
        "SIN_NEGOCIAR" | "SIN_CESION"  | null
        // vigencia rechazada antes de A3 (la creación ya REVERTIDA) o legado: sin firma en curso
        "SIN_NEGOCIAR" | "SIN_CESION"  | "NEG-VIEJA"
        "SIN_NEGOCIAR" | "EN_REVISION" | null
    }

    @Unroll
    def "fila #fila (#estado + #firma): no se toca, ni revierte ni cambia el estado"() {
        given:
        def f = factura(estado, firma, "NEG-1")
        facturaPort.findCandidatasVencimiento(_, _) >> [f]
        operaciones.buscar(_, _) >> Optional.empty()

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        0 * facturaPatch.marcarVencidaSiCoincide(*_)
        0 * reversionExacta._
        r.vencidas() == 0
        r.protegidas() == 1

        where:
        fila | estado         | firma
        6    | "NEGOCIADA"    | "CESION"
        6    | "NEGOCIADA"    | "DESCUENTO"
        6    | "NEGOCIADA"    | "APROBACION"
        9    | "NEGOCIADA"    | "RECHAZADA"
        // estados terminales: la consulta ya no los trae; si llegaran, tampoco se tocan
        7    | "LIQUIDADA"    | "CESION"
        8    | "RECHAZADA"    | "RECHAZADA"
        10   | "VENCIDA"      | "SIN_CESION"
        11   | "BLOQUEADA"    | null
        11   | "VENDIDA"      | null
        11   | "INEXISTENTE"  | null
    }

    def "fila 6 por registros: con seguimiento de liquidación (aunque la firma no sea la final) no se toca"() {
        given:
        def f = factura("NEGOCIADA", "EN_APROBACION", "NEG-1")
        facturaPort.findCandidatasVencimiento(_, _) >> [f]
        operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE, "NEG-1|" + f.cufe) >>
                Optional.of(new OperacionPendiente(7L, TipoOperacionPendiente.LIQUIDACION_PENDIENTE, "NEG-1|" + f.cufe,
                        SeguimientoLiquidacionService.SOLICITADA, "{}", null, 0, AHORA, AHORA, null))

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        0 * facturaPatch.marcarVencidaSiCoincide(*_)
        0 * reversionExacta._
        r.protegidas() == 1
    }

    def "compare-and-set perdido (la firma final ganó entre la lectura y la escritura): no revierte"() {
        given:
        def f = factura("NEGOCIADA", "EN_APROBACION", "NEG-1")
        facturaPort.findCandidatasVencimiento(_, _) >> [f]
        operaciones.buscar(_, _) >> Optional.empty()

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        1 * facturaPatch.marcarVencidaSiCoincide(f.id, "NEGOCIADA", "EN_APROBACION", true) >> false
        0 * reversionExacta._
        0 * facturaPatch.quitarReversionPendiente(_)
        r.perdidas() == 1
        r.vencidas() == 0
    }

    def "más de 10 días para vencer: no se toca"() {
        given:
        def f = factura("NEGOCIADA", "SIN_CESION", "NEG-1", 11)
        facturaPort.findCandidatasVencimiento(_, _) >> [f]

        when:
        service.actualizarFacturasVencidas()

        then:
        0 * facturaPatch._
        0 * reversionExacta._
    }

    def "caída a mitad de la corrida: la reversión falla, la marca queda y la corrida siguiente la retoma una vez"() {
        given:
        def f = factura("NEGOCIADA", "SIN_CESION", "NEG-1")
        def vencida = factura("NEGOCIADA", "SIN_CESION", "NEG-1")
        vencida.estadoNegociacion = "VENCIDA"
        vencida.vencimientoReversionPendiente = true
        operaciones.buscar(_, _) >> Optional.empty()

        when: "primera corrida: VENCIDA y la reversión se cae (MySQL no responde)"
        def r1 = service.actualizarFacturasVencidas()

        then:
        1 * facturaPort.findCandidatasVencimiento(_, _) >> [f]
        1 * facturaPatch.marcarVencidaSiCoincide(f.id, "NEGOCIADA", "SIN_CESION", true) >> true
        1 * reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO, "NEG-1", f.cufe, _) >>
                { throw new IllegalStateException("MySQL caído") }
        0 * facturaPatch.quitarReversionPendiente(_)
        r1.vencidas() == 1
        r1.fallidas() == 1

        when: "segunda corrida: la retoma desde la marca y ya no es candidata (está VENCIDA)"
        def r2 = service.actualizarFacturasVencidas()

        then:
        1 * facturaPort.findVencidasConReversionPendiente() >> [vencida]
        1 * facturaPort.findCandidatasVencimiento(_, _) >> []
        1 * reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO, "NEG-1", vencida.cufe, _) >>
                ReversionExactaService.Resultado.REVERTIDA
        1 * facturaPatch.quitarReversionPendiente(vencida.id)
        r2.revertidas() == 1
        r2.vencidas() == 0
    }

    def "reanudar una reversión que ya se había confirmado no devuelve otra vez (YA_REVERTIDA) y quita la marca"() {
        given:
        def vencida = factura("VENCIDA", "SIN_CESION", "NEG-1")
        vencida.vencimientoReversionPendiente = true
        facturaPort.findCandidatasVencimiento(_, _) >> []

        when:
        service.actualizarFacturasVencidas()

        then:
        1 * facturaPort.findVencidasConReversionPendiente() >> [vencida]
        1 * reversionExacta.revertir(TipoOperacionPendiente.VENCIMIENTO, "NEG-1", vencida.cufe, _) >>
                ReversionExactaService.Resultado.YA_REVERTIDA
        1 * facturaPatch.quitarReversionPendiente(vencida.id)
    }

    def "un error en una factura no frena las demás"() {
        given:
        def mala = factura("NEGOCIADA", "SIN_CESION", "NEG-1")
        def buena = factura("CARGADA", "SIN_FIRMA", null)
        facturaPort.findCandidatasVencimiento(_, _) >> [mala, buena]
        operaciones.buscar(_, _) >> { throw new IllegalStateException("MySQL caído") }

        when:
        def r = service.actualizarFacturasVencidas()

        then:
        1 * facturaPatch.marcarVencidaSiCoincide(buena.id, "CARGADA", "SIN_FIRMA", false) >> true
        r.fallidas() == 1
        r.vencidas() == 1
    }

    def "la consulta pide hoy + 10 días y solo los estados candidatos"() {
        when:
        service.actualizarFacturasVencidas()

        then:
        1 * facturaPort.findCandidatasVencimiento(LocalDate.of(2026, 12, 1),
                ["CARGADA", "SIN_NEGOCIAR", "NEGOCIADA", "ENDOSADA"]) >> []
    }
}
