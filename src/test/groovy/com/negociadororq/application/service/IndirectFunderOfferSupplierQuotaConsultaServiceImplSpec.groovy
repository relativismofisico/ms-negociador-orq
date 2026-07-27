package com.negociadororq.application.service

import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort
import com.negociadororq.domain.model.IndirectFunderOffer
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota
import spock.lang.Specification

class IndirectFunderOfferSupplierQuotaConsultaServiceImplSpec extends Specification {

    PayerFundingVisibilityOutputPort payerFundingVisibilityPort = Mock()
    IndirectFunderOfferOutputPort indirectFunderOfferPort = Mock()
    IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort = Mock()

    IndirectFunderOfferSupplierQuotaConsultaServiceImpl service = new IndirectFunderOfferSupplierQuotaConsultaServiceImpl(
            payerFundingVisibilityPort, indirectFunderOfferPort, indirectFunderOfferSupplierQuotaPort)

    def "obtenerCupo devuelve null si el pagador no habilito el toggle"() {
        given:
        payerFundingVisibilityPort.estaHabilitado(30L) >> false

        when:
        def resultado = service.obtenerCupo(20L, 30L, 10L)

        then:
        resultado == null
        0 * indirectFunderOfferPort.findActivaAprobada(_, _)
    }

    def "obtenerCupo devuelve null si no hay oferta aprobada vigente"() {
        given:
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.empty()

        when:
        def resultado = service.obtenerCupo(20L, 30L, 10L)

        then:
        resultado == null
    }

    def "obtenerCupo devuelve el cupo integro de la oferta si el proveedor aun no negocio contra ella"() {
        given:
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        def oferta = IndirectFunderOffer.builder().id(77L).funderId(20L).payerId(30L)
                .payerRate(new BigDecimal("2.5")).supplierQuotaTemplate(new BigDecimal("10000.00")).build()
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.of(oferta)
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.empty()

        when:
        def resultado = service.obtenerCupo(20L, 30L, 10L)

        then:
        resultado.tasa == new BigDecimal("2.5")
        resultado.cupoDisponible == new BigDecimal("10000.00")
    }

    def "obtenerCupo devuelve el cupo consumido restante si el proveedor ya negocio contra la oferta"() {
        given:
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        def oferta = IndirectFunderOffer.builder().id(77L).funderId(20L).payerId(30L)
                .payerRate(new BigDecimal("2.5")).supplierQuotaTemplate(new BigDecimal("10000.00")).build()
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.of(oferta)
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.of(
                IndirectFunderOfferSupplierQuota.builder().currentQuota(new BigDecimal("6000.00")).build()
        )

        when:
        def resultado = service.obtenerCupo(20L, 30L, 10L)

        then:
        resultado.cupoDisponible == new BigDecimal("6000.00")
        resultado.funderId == 20L
        resultado.payerId == 30L
        resultado.supplierId == 10L
    }
}
