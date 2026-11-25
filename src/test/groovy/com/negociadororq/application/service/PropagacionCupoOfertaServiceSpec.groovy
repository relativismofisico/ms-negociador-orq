package com.negociadororq.application.service

import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort
import spock.lang.Specification
import spock.lang.Unroll

/** Fase 28d (P-19): el servicio delega la propagación y rechaza eventos inválidos sin tocar los cupos. */
class PropagacionCupoOfertaServiceSpec extends Specification {

    IndirectFunderOfferSupplierQuotaOutputPort cupos = Mock()
    PropagacionCupoOfertaService servicio = new PropagacionCupoOfertaService(cupos)

    def "aplica el cambio con la oferta, la versión y el cupo nuevo"() {
        when:
        def p = servicio.aplicar(7L, 2L, new BigDecimal("15000.00"))

        then:
        1 * cupos.propagarCupoOferta(7L, 2L, new BigDecimal("15000.00")) >>
                new IndirectFunderOfferSupplierQuotaOutputPort.Propagacion(3, 1L)
        p.ajustadas() == 3
        p.porEncima() == 1L
    }

    @Unroll
    def "rechaza un evento inválido (#caso) sin tocar los cupos"() {
        when:
        servicio.aplicar(oferta, 1L, nuevo)

        then:
        thrown(IllegalArgumentException)
        0 * cupos._

        where:
        caso           | oferta | nuevo
        "sin oferta"   | null   | new BigDecimal("1.00")
        "sin cupo"     | 7L     | null
        "cupo negativo"| 7L     | new BigDecimal("-0.01")
    }
}
