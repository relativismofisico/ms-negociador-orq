package com.negociadororq.application.service

import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort
import com.negociadororq.application.port.output.OperacionPendienteOutputPort
import com.negociadororq.domain.model.DescuentoCupos
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.TipoOperacionPendiente
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionOperations
import spock.lang.Specification

import java.time.LocalDateTime

/** Fase 28d (A2): la reversión exacta decide qué hacer según el registro (la conservación al centavo: ConservacionCuposIT). */
class ReversionExactaServiceSpec extends Specification {

    OperacionPendienteOutputPort operaciones = Mock()
    CupoReversionService cupos = Mock()
    IndirectFunderOfferSupplierQuotaOutputPort indirecto = Mock()
    TransactionOperations tx = Stub() { execute(_) >> { TransactionCallback c -> c.doInTransaction(null) } }
    AuditoriaNegociacionService auditoria = Mock()
    CorreosReversionService correos = Mock()
    ReversionExactaService servicio = new ReversionExactaService(operaciones, cupos, indirecto, tx, auditoria, correos)

    static final def CTX = new DescuentoCupos.Contexto("FACTORING", "PROV", "PAG", "F1")

    static OperacionPendiente registro(String estado, String datos) {
        new OperacionPendiente(1L, TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1|CUFE-1", estado, datos, null, 0,
                LocalDateTime.now(), LocalDateTime.now(), null)
    }

    static String datos(List<DescuentoCupos.Descuento> d) {
        new DescuentoCupos(d, CTX).aJson()
    }

    def "devuelve exacto cada cupo del registro, con la auditoría y la plantilla A en la misma transacción"() {
        given:
        def d = [new DescuentoCupos.Descuento(DescuentoCupos.Tipo.OPERADOR, 1L, null, 10.01G),
                 new DescuentoCupos.Descuento(DescuentoCupos.Tipo.EMPRESA, 2L, null, 10.01G),
                 new DescuentoCupos.Descuento(DescuentoCupos.Tipo.FONDEADOR_FACTORING, 3L, 4L, 10.01G),
                 new DescuentoCupos.Descuento(DescuentoCupos.Tipo.FONDEADOR_CONFIRMING, 3L, 5L, 10.01G),
                 new DescuentoCupos.Descuento(DescuentoCupos.Tipo.INDIRECTO, 77L, 4L, 10.01G)]
        operaciones.buscar(TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1|CUFE-1") >> Optional.of(registro("COMPLETADA", datos(d)))

        when:
        def r = servicio.revertir(TipoOperacionPendiente.ENDOSO_RECHAZADO, "neg-1", "CUFE-1", "vigencia")

        then:
        r == ReversionExactaService.Resultado.REVERTIDA
        1 * operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1|CUFE-1", "COMPLETADA", "REVERTIDA") >> true
        1 * cupos.revertirCupoUsuario(1L, 10.01G)
        1 * cupos.revertirCupoEmpresaEnTransaccionActual(2L, 10.01G)
        1 * cupos.revertirCupoFondeadorFactoring(3L, 4L, 10.01G)
        1 * cupos.revertirCupoFondeadorConfirming(3L, 5L, 10.01G)
        1 * indirecto.devolverCupo(77L, 4L, 10.01G) >> 1
        1 * auditoria.cuposRevertidos("neg-1", "CUFE-1", "ENDOSO_RECHAZADO", 5)
        1 * correos.revertida("neg-1", "CUFE-1", CTX)
    }

    def "Fase 28d (decisión de John): #camino devuelve exacto pero sin plantilla A (ya tiene su propio correo)"() {
        given:
        operaciones.buscar(*_) >> Optional.of(registro("COMPLETADA",
                datos([new DescuentoCupos.Descuento(DescuentoCupos.Tipo.OPERADOR, 1L, null, 1G)])))
        operaciones.transicionar(_, _, "COMPLETADA", "REVERTIDA") >> true

        when:
        def r = servicio.revertir(camino, "neg-1", "CUFE-1", "motivo")

        then:
        r == ReversionExactaService.Resultado.REVERTIDA
        1 * cupos.revertirCupoUsuario(1L, 1G)
        1 * auditoria.cuposRevertidos("neg-1", "CUFE-1", camino.name(), 1)
        0 * correos.revertida(*_)

        where:
        camino << [TipoOperacionPendiente.RECHAZO, TipoOperacionPendiente.LIQUIDACION_FALLIDA]
    }

    def "desde CUPOS_DESCONTADOS (creación sin terminar) también revierte"() {
        given:
        operaciones.buscar(*_) >> Optional.of(registro("CUPOS_DESCONTADOS",
                datos([new DescuentoCupos.Descuento(DescuentoCupos.Tipo.OPERADOR, 1L, null, 1G)])))

        when:
        def r = servicio.revertir(TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1", "CUFE-1", "x")

        then:
        r == ReversionExactaService.Resultado.REVERTIDA
        1 * operaciones.transicionar(_, _, "COMPLETADA", "REVERTIDA") >> false
        1 * operaciones.transicionar(_, _, "CUPOS_DESCONTADOS", "REVERTIDA") >> true
        1 * cupos.revertirCupoUsuario(1L, 1G)
    }

    def "ya REVERTIDA o CAS perdido: no devuelve nada ni envía correo"() {
        given:
        operaciones.buscar(*_) >> Optional.of(registro(estado, datos([new DescuentoCupos.Descuento(
                DescuentoCupos.Tipo.OPERADOR, 1L, null, 1G)])))
        operaciones.transicionar(*_) >> false

        when:
        def r = servicio.revertir(TipoOperacionPendiente.VENCIMIENTO, "neg-1", "CUFE-1", "x")

        then:
        r == ReversionExactaService.Resultado.YA_REVERTIDA
        0 * cupos._
        0 * correos._
        0 * auditoria._

        where:
        estado << ["REVERTIDA", "COMPLETADA"]
    }

    def "sin registro, sin datos o con otro formato: REVISION_MANUAL y plantilla B en la misma transacción; nada se devuelve"() {
        given:
        operaciones.buscar(*_) >> encontrado

        when:
        def r = servicio.revertir(TipoOperacionPendiente.LIQUIDACION_FALLIDA, "neg-1", "CUFE-1", 'motivo "con" comillas')

        then:
        r == ReversionExactaService.Resultado.SIN_REGISTRO
        1 * operaciones.registrar(TipoOperacionPendiente.LIQUIDACION_FALLIDA, "neg-1|CUFE-1", "REVISION_MANUAL",
                { !it.contains('"con"') }) >> true
        1 * correos.enRevision("neg-1", "CUFE-1", null)
        0 * cupos._

        where:
        encontrado << [Optional.empty(), Optional.of(registro("COMPLETADA", null)),
                       Optional.of(registro("COMPLETADA", '{"otro":"formato"}'))]
    }

    def "REVISION_MANUAL ya registrada para ese camino: no se repite la plantilla B"() {
        given:
        operaciones.buscar(*_) >> Optional.empty()
        operaciones.registrar(*_) >> false

        when:
        servicio.revertir(TipoOperacionPendiente.RECHAZO, "neg-1", "CUFE-1", null)

        then:
        0 * correos._
    }

    def "la fila indirecta no existe: falla y nada queda confirmado (la transacción se revierte)"() {
        given:
        operaciones.buscar(*_) >> Optional.of(registro("COMPLETADA", datos([new DescuentoCupos.Descuento(
                DescuentoCupos.Tipo.INDIRECTO, 77L, 4L, 1G)])))
        operaciones.transicionar(*_) >> true
        indirecto.devolverCupo(*_) >> 0

        when:
        servicio.revertir(TipoOperacionPendiente.RECHAZO, "neg-1", "CUFE-1", "x")

        then:
        thrown(IllegalStateException)
        0 * correos._
    }
}
