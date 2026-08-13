package com.negociadororq.application.util

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class VigenciaDescuentoValidatorSpec extends Specification {

    @Shared
    def hoy = LocalDate.of(2026, 6, 15)

    def "TODO_EL_PLAZO nunca bloquea"() {
        expect:
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, null, hoy.plusDays(1), "TODO_EL_PLAZO", null)
    }

    def "vigenciaDescuento null nunca bloquea (compatibilidad con asignaciones sin parametrizar)"() {
        expect:
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, null, hoy.plusDays(1), null, null)
    }

    @Unroll
    def "PRIMEROS_DIAS: dentro=#dentro cuando fechaNegociacion=#fechaNegociacion, diasVigencia=#dias"() {
        expect:
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, fechaNegociacion, null, "PRIMEROS_DIAS", dias) == dentro

        where:
        fechaNegociacion   | dias | dentro
        null               | 5    | true  // aun no negociada: trivialmente dentro (chequeo real es al endosar)
        hoy.minusDays(3)    | 5    | true  // dentro de la ventana
        hoy.minusDays(5)    | 5    | true  // justo en el limite (inclusive)
        hoy.minusDays(6)    | 5    | false // ya se paso
    }

    @Unroll
    def "DIAS_ANTES_VENCIMIENTO: dentro=#dentro cuando fechaVencimiento=#fechaVencimiento, diasVigencia=#dias"() {
        expect:
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, null, fechaVencimiento, "DIAS_ANTES_VENCIMIENTO", dias) == dentro

        where:
        fechaVencimiento   | dias | dentro
        hoy.plusDays(30)    | 10   | true  // todavia lejos del limite
        hoy.plusDays(10)    | 10   | true  // justo en el limite (inclusive)
        hoy.plusDays(5)     | 10   | false // ya esta dentro de la ventana de exclusion
        null                | 10   | true  // sin fecha de vencimiento: no se puede evaluar, no bloquea
    }

    def "diasVigencia null nunca bloquea, sin importar la variante"() {
        expect:
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, hoy.minusDays(100), hoy.plusDays(1), "PRIMEROS_DIAS", null)
        VigenciaDescuentoValidator.dentroDeVigencia(hoy, hoy.minusDays(100), hoy.minusDays(1), "DIAS_ANTES_VENCIMIENTO", null)
    }
}
