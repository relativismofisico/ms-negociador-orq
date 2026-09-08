package com.negociadororq.infrastructure.persistence.adapter

import com.negociadororq.domain.model.Endoso
import com.negociadororq.infrastructure.persistence.entity.EndosoDocument
import com.negociadororq.infrastructure.persistence.repository.EndosoRepository
import spock.lang.Specification

class EndosoAdapterSpec extends Specification {

    EndosoRepository repository = Mock()
    EndosoAdapter adapter = new EndosoAdapter(repository)

    def "save mapea cufes en ambos sentidos (dominio -> documento -> dominio), paralelo a numerosFactura"() {
        given:
        def endoso = Endoso.builder()
                .negociacionId("NEG-1")
                .numerosFactura(["FE-1", "FE-2"])
                .cufes(["CUFE-AAA", "CUFE-BBB"])
                .rutProveedor("PROV")
                .build()

        when:
        def guardado = adapter.save(endoso)

        then:
        1 * repository.save({ EndosoDocument doc ->
            doc.numerosFactura == ["FE-1", "FE-2"] && doc.cufes == ["CUFE-AAA", "CUFE-BBB"]
        }) >> { EndosoDocument doc -> doc }
        guardado.cufes == ["CUFE-AAA", "CUFE-BBB"]
        guardado.numerosFactura == ["FE-1", "FE-2"]
    }

    def "save tolera cufes null (endoso viejo) sin romper el mapeo"() {
        given:
        def endoso = Endoso.builder()
                .negociacionId("NEG-1")
                .numerosFactura(["FE-1"])
                .cufes(null)
                .build()

        when:
        def guardado = adapter.save(endoso)

        then:
        1 * repository.save({ EndosoDocument doc -> doc.cufes == null }) >> { EndosoDocument doc -> doc }
        guardado.cufes == null
    }

    def "findByNegociacionIdAndNumeroFactura devuelve el endoso con sus cufes"() {
        given:
        def doc = EndosoDocument.builder()
                .id("END-1")
                .negociacionId("NEG-1")
                .numerosFactura(["FE-1"])
                .cufes(["CUFE-AAA"])
                .build()

        when:
        def resultado = adapter.findByNegociacionIdAndNumeroFactura("NEG-1", "FE-1")

        then:
        1 * repository.findByNegociacionIdAndNumerosFacturaContains("NEG-1", "FE-1") >> Optional.of(doc)
        resultado.isPresent()
        resultado.get().cufes == ["CUFE-AAA"]
    }
}
