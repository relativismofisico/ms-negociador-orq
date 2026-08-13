package com.negociadororq.application.service

import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota
import spock.lang.Specification

class IndirectFunderOfferSupplierQuotaRecargaServiceImplSpec extends Specification {

    IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort = Mock()

    IndirectFunderOfferSupplierQuotaRecargaServiceImpl service =
            new IndirectFunderOfferSupplierQuotaRecargaServiceImpl(indirectFunderOfferSupplierQuotaPort)

    def "recargarCupo lanza RecursoNoEncontradoException si el proveedor nunca negocio contra la oferta"() {
        given:
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.empty()

        when:
        service.recargarCupo(77L, 10L)

        then:
        thrown(RecursoNoEncontradoException)
        0 * indirectFunderOfferSupplierQuotaPort.save(_)
    }

    def "recargarCupo restaura currentQuota al valor de originalQuota sin tocar otros campos"() {
        given:
        def cupoProveedor = IndirectFunderOfferSupplierQuota.builder()
                .id(1L).offerId(77L).supplierId(10L).rate(new BigDecimal("2.5"))
                .quota(new BigDecimal("10000.00")).currentQuota(new BigDecimal("4000.00"))
                .originalQuota(new BigDecimal("10000.00")).build()
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.of(cupoProveedor)

        when:
        def resultado = service.recargarCupo(77L, 10L)

        then:
        1 * indirectFunderOfferSupplierQuotaPort.save({ it.currentQuota == new BigDecimal("10000.00") }) >> cupoProveedor
        resultado.offerId == 77L
        resultado.supplierId == 10L
        resultado.cupoDisponible == new BigDecimal("10000.00")
    }
}
