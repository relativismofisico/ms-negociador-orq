package com.negociadororq.domain.model

import spock.lang.Specification

/** Fase 28d (A2): el registro exacto de lo descontado se guarda y se lee sin perder un centavo ni adivinar nada. */
class DescuentoCuposSpec extends Specification {

    static List<DescuentoCupos.Descuento> descuentos() {
        [new DescuentoCupos.Descuento(DescuentoCupos.Tipo.OPERADOR, 1L, null, new BigDecimal("950000.37")),
         new DescuentoCupos.Descuento(DescuentoCupos.Tipo.INDIRECTO, 77L, 31L, new BigDecimal("0.01"))]
    }

    def "ida y vuelta exacta, con y sin contexto (con comillas y barras en el contexto)"() {
        given:
        def original = new DescuentoCupos(descuentos(), contexto)

        when:
        def leido = DescuentoCupos.deJson(original.aJson())

        then:
        leido == original
        leido.descuentos()[0].monto().toPlainString() == "950000.37"
        !OperacionPendiente.exigirDatosNoSensibles(original.aJson()).isEmpty()

        where:
        contexto << [null, new DescuentoCupos.Contexto("FACTORING", "PROV", "PAG", 'F-"1"\\x')]
    }

    def "un JSON con otro formato o versión no se adivina: IllegalArgumentException"() {
        when:
        DescuentoCupos.deJson(json)

        then:
        thrown(IllegalArgumentException)

        where:
        json << [null, "{}", '{"v":2,"descuentos":[]}', '{"v":1,"descuentos":[{"tipo":"OPERADOR"}]}',
                 '{"v":1,"descuentos":[],"contexto":{"x":1}}']
    }
}
