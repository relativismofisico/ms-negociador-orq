package com.negociadororq.application.util

import spock.lang.Specification
import spock.lang.Unroll

/** Fase 28d (D2/D4): la fórmula del techo con objetivo, exacta en BigDecimal. */
class CupoConObjetivoSpec extends Specification {

    @Unroll
    def "techo #techo, disponible #disponible, objetivo #objetivo, devuelto #devuelto -> techo #t, disponible #d, objetivo #o"() {
        when:
        def r = CupoConObjetivo.devolver(new BigDecimal(techo), new BigDecimal(disponible),
                objetivo == null ? null : new BigDecimal(objetivo), new BigDecimal(devuelto))

        then:
        r.techo().toPlainString() == t
        r.disponible().toPlainString() == d
        r.objetivo()?.toPlainString() == o

        where:
        techo      | disponible | objetivo  | devuelto  || t          | d         | o
        // caso de John: techo 10.000 -> exposición 7.000 -> baja a 5.000 (techo 7.000, disp 0) -> devuelve 4.000
        "7000.00"  | "0.00"     | "5000.00" | "4000.00" || "5000.00"  | "2000.00" | null
        // todavía por encima del objetivo: el techo baja hasta la exposición, disponible 0
        "7000.00"  | "0.00"     | "5000.00" | "1000.00" || "6000.00"  | "0.00"    | "5000.00"
        // sin objetivo: MIN(techo, disponible + devuelto)
        "1000.00"  | "900.00"   | null      | "300.00"  || "1000.00"  | "1000.00" | null
        "1000.00"  | "100.00"   | null      | "300.00"  || "1000.00"  | "400.00"  | null
        // D4: operador con objetivo 0 (desactivado), drena hasta 0
        "300.00"   | "0.00"     | "0.00"    | "100.00"  || "200.00"   | "0.00"    | "0.00"
        "300.00"   | "0.00"     | "0.00"    | "300.00"  || "0.00"     | "0.00"    | null
        "300.00"   | "0.00"     | "0.00"    | "500.00"  || "0.00"     | "0.00"    | null
    }

    def "bajaDelTecho: cuánto bajó (nunca negativo)"() {
        expect:
        CupoConObjetivo.devolver(7000.00G, 0.00G, 5000.00G, 4000.00G).bajaDelTecho(7000.00G) == 2000.00G
        CupoConObjetivo.devolver(1000.00G, 900.00G, null, 300.00G).bajaDelTecho(1000.00G) == 0
    }
}
