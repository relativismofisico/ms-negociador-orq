package com.negociadororq.application.service.notificacion.impl

import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.math.BigDecimal

class BuildersSpec extends Specification {

    // ─── FacturaCuerpoBuilder ─────────────────────────────────────────────────

    FacturaCuerpoBuilder facturaBuilder = new FacturaCuerpoBuilder()

    def "FacturaCuerpoBuilder construye mensaje con datos de factura"() {
        given:
        def factura = new Factura()
        factura.numeroFactura = "FECI-TEST"
        factura.rutProveedor = "900111222"
        factura.valorFactura = new BigDecimal("1500.50")

        when:
        def cuerpo = facturaBuilder.construir(factura)

        then:
        cuerpo.contains("FECI-TEST")
        cuerpo.contains("900111222")
        cuerpo.contains("1500")
        cuerpo.contains("Nueva factura")
    }

    def "FacturaCuerpoBuilder getTipo retorna Factura"() {
        expect:
        facturaBuilder.getTipo() == Factura.class
    }

    def "FacturaCuerpoBuilder con valores null no lanza excepcion"() {
        given:
        def factura = new Factura()
        factura.numeroFactura = null
        factura.rutProveedor = null
        factura.valorFactura = null

        when:
        def cuerpo = facturaBuilder.construir(factura)

        then:
        cuerpo != null
        cuerpo.contains("Nueva factura")
    }

    // ─── NegociacionCuerpoBuilder ─────────────────────────────────────────────

    NegociacionCuerpoBuilder negBuilder = new NegociacionCuerpoBuilder()

    def "NegociacionCuerpoBuilder construye mensaje con datos de negociacion"() {
        given:
        def neg = Negociacion.builder()
                .id("NEG-100")
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .totalNegociado(new BigDecimal("25000.00"))
                .facturas([FacturaNegociada.builder().build(), FacturaNegociada.builder().build()])
                .build()

        when:
        def cuerpo = negBuilder.construir(neg)

        then:
        cuerpo.contains("NEG-100")
        cuerpo.contains("CONFIRMING")
        cuerpo.contains("25000")
        cuerpo.contains("2") // 2 facturas
        cuerpo.contains("nueva negociación")
    }

    def "NegociacionCuerpoBuilder getTipo retorna Negociacion"() {
        expect:
        negBuilder.getTipo() == Negociacion.class
    }

    // ─── PersonaCuerpoBuilder ─────────────────────────────────────────────────

    PersonaCuerpoBuilder personaBuilder = new PersonaCuerpoBuilder()

    def "PersonaCuerpoBuilder construye mensaje con datos de usuario"() {
        given:
        def user = User.builder()
                .ideUser(99L)
                .username("usr-operador")
                .build()

        when:
        def cuerpo = personaBuilder.construir(user)

        then:
        cuerpo.contains("usr-operador")
        cuerpo.contains("99")
        cuerpo.contains("persona registrada")
    }

    def "PersonaCuerpoBuilder getTipo retorna User"() {
        expect:
        personaBuilder.getTipo() == User.class
    }
}