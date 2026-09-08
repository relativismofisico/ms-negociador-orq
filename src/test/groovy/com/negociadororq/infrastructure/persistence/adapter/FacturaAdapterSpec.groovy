package com.negociadororq.infrastructure.persistence.adapter

import com.negociadororq.infrastructure.persistence.entity.FacturaDocument
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository
import spock.lang.Specification

class FacturaAdapterSpec extends Specification {

    FacturaRepository repository = Mock()
    FacturaAdapter adapter = new FacturaAdapter(repository)

    def "findByNumeroFactura usa findFirst...OrderByIdDesc (numeroFactura no es unico global)"() {
        given:
        def doc = new FacturaDocument()
        doc.id = "id-mas-reciente"
        doc.numeroFactura = "FE-1"
        doc.valorNetoFactura = 1_000_000

        when:
        def result = adapter.findByNumeroFactura("FE-1")

        then:
        1 * repository.findFirstByNumeroFacturaOrderByIdDesc("FE-1") >> Optional.of(doc)
        0 * repository.findByNumeroFactura(_)
        result.isPresent()
        result.get().numeroFactura == "FE-1"
        result.get().valorNetoFactura == 1_000_000
    }

    def "findByNumeroFactura devuelve vacio cuando no hay factura con ese numero"() {
        when:
        def result = adapter.findByNumeroFactura("NO-EXISTE")

        then:
        1 * repository.findFirstByNumeroFacturaOrderByIdDesc("NO-EXISTE") >> Optional.empty()
        result.isEmpty()
    }
}
