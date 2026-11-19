package com.negociadororq.infrastructure.persistence.adapter

import com.negociadororq.infrastructure.persistence.entity.FacturaDocument
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository
import spock.lang.Specification

class FacturaAdapterSpec extends Specification {

    FacturaRepository repository = Mock()
    FacturaAdapter adapter = new FacturaAdapter(repository)

    static FacturaDocument doc(String id, String numero, String proveedor) {
        def d = new FacturaDocument()
        d.id = id
        d.numeroFactura = numero
        d.rutProveedor = proveedor
        d.cufe = "CUFE-" + id
        d
    }

    def "Fase 28d (C): findAllByNumeroFactura devuelve TODAS las facturas con ese número (no elige la más reciente)"() {
        when:
        def result = adapter.findAllByNumeroFactura("FE-1")

        then:
        1 * repository.findByNumeroFactura("FE-1") >> [doc("a", "FE-1", "PROV-A"), doc("b", "FE-1", "PROV-B")]
        result*.rutProveedor == ["PROV-A", "PROV-B"]
        result*.cufe == ["CUFE-a", "CUFE-b"]
    }

    def "Fase 28d (C): findByCufe por la clave única"() {
        when:
        def result = adapter.findByCufe("CUFE-a")

        then:
        1 * repository.findByCufe("CUFE-a") >> Optional.of(doc("a", "FE-1", "PROV-A"))
        result.get().numeroFactura == "FE-1"
    }
}
