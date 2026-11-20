package com.negociadororq.application.service

import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.MarcaNegociacionOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.OperacionPendienteOutputPort
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.OperacionPendiente
import com.negociadororq.domain.model.ReferenciaFactura
import com.negociadororq.domain.model.TipoOperacionPendiente
import spock.lang.Specification

import java.time.LocalDateTime

/** Fase 28d (A2): reanudador de CREAR_NEGOCIACION (marcador CANCELADA primero; completa o revierte exacto). */
class ReanudadorCrearNegociacionSpec extends Specification {

    MarcaNegociacionOutputPort marca = Mock()
    NegociacionOutputPort negociacionPort = Mock()
    OperacionPendienteOutputPort operaciones = Mock()
    FacturaPatchOutputPort facturaPatch = Mock()
    ReversionExactaService reversion = Mock()
    ReanudadorCrearNegociacion reanudador = new ReanudadorCrearNegociacion(marca, negociacionPort, operaciones,
            facturaPatch, reversion)

    static OperacionPendiente op() {
        new OperacionPendiente(1L, TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1|CUFE-1", "CUPOS_DESCONTADOS", "{}",
                null, 1, LocalDateTime.now(), LocalDateTime.now(), null)
    }

    def "tipo y estados: CREAR_NEGOCIACION desde CUPOS_DESCONTADOS"() {
        expect:
        reanudador.tipo() == TipoOperacionPendiente.CREAR_NEGOCIACION
        reanudador.estadosReanudables() == ["CUPOS_DESCONTADOS"]
    }

    def "la negociación no llegó a Mongo: el marcador CANCELADA entra y se revierte exacto (plantilla A en la reversión)"() {
        when:
        reanudador.reanudar(op())

        then:
        1 * marca.crear("neg-1", MarcaNegociacionOutputPort.Tipo.CANCELADA) >> true
        1 * reversion.revertir(TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1", "CUFE-1", _)
        0 * operaciones.transicionar(*_)
    }

    def "otra factura de la misma negociación ya la canceló: esta también se revierte"() {
        when:
        reanudador.reanudar(op())

        then:
        1 * marca.crear("neg-1", MarcaNegociacionOutputPort.Tipo.CANCELADA) >> false
        1 * marca.buscar("neg-1") >> Optional.of(MarcaNegociacionOutputPort.Tipo.CANCELADA)
        1 * reversion.revertir(_, "neg-1", "CUFE-1", _)
    }

    def "la negociación existe (marcador CREADA): completa, repara la factura y NO revierte"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(Negociacion.builder().id("neg-1").facturas([
                FacturaNegociada.builder().numeroFactura("F1").cufe("CUFE-1").rutProveedor("PROV").rutPagador("PAG")
                        .build()]).build())

        when:
        reanudador.reanudar(op())

        then:
        1 * marca.crear("neg-1", MarcaNegociacionOutputPort.Tipo.CANCELADA) >> false
        1 * marca.buscar("neg-1") >> Optional.of(MarcaNegociacionOutputPort.Tipo.CREADA)
        1 * facturaPatch.actualizarNegociacionIdPorCufe(new ReferenciaFactura("CUFE-1", "PROV", "PAG"), "neg-1")
        1 * operaciones.transicionar(TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-1|CUFE-1", "CUPOS_DESCONTADOS",
                "COMPLETADA") >> true
        0 * reversion._
    }
}
