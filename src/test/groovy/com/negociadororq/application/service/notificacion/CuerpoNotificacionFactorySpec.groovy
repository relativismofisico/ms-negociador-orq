package com.negociadororq.application.service.notificacion

import com.negociadororq.application.service.notificacion.impl.FacturaCuerpoBuilder
import com.negociadororq.application.service.notificacion.impl.NegociacionCuerpoBuilder
import com.negociadororq.application.service.notificacion.impl.PersonaCuerpoBuilder
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.math.BigDecimal

class CuerpoNotificacionFactorySpec extends Specification {

    FacturaCuerpoBuilder facturaCuerpoBuilder = new FacturaCuerpoBuilder()
    NegociacionCuerpoBuilder negociacionCuerpoBuilder = new NegociacionCuerpoBuilder()
    PersonaCuerpoBuilder personaCuerpoBuilder = new PersonaCuerpoBuilder()

    CuerpoNotificacionFactory factory

    def setup() {
        factory = new CuerpoNotificacionFactory([facturaCuerpoBuilder, negociacionCuerpoBuilder, personaCuerpoBuilder])
        factory.init()
    }

    def "construir con Factura delega a FacturaCuerpoBuilder"() {
        given:
        def factura = new Factura()
        factura.numeroFactura = "FECI001"
        factura.rutProveedor = "900000001"
        factura.valorFactura = new BigDecimal("5000.00")

        when:
        def resultado = factory.construir(factura)

        then:
        resultado.contains("FECI001")
        resultado.contains("900000001")
        resultado.contains("5000")
    }

    def "construir con Negociacion delega a NegociacionCuerpoBuilder"() {
        given:
        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .totalNegociado(new BigDecimal("10000.00"))
                .facturas([FacturaNegociada.builder().build()])
                .build()

        when:
        def resultado = factory.construir(neg)

        then:
        resultado.contains("neg-001")
        resultado.contains("FACTORING")
        resultado.contains("10000")
    }

    def "construir con User delega a PersonaCuerpoBuilder"() {
        given:
        def user = User.builder()
                .ideUser(42L)
                .username("operador01")
                .build()

        when:
        def resultado = factory.construir(user)

        then:
        resultado.contains("operador01")
        resultado.contains("42")
    }

    def "construir con tipo desconocido retorna mensaje sin builder"() {
        given:
        def objeto = new Object()

        when:
        def resultado = factory.construir(objeto)

        then:
        resultado.contains("No existe builder")
        resultado.contains("Object")
    }

    def "init() carga todos los builders correctamente"() {
        expect:
        factory != null
    }

    def "FacturaCuerpoBuilder retorna clase Factura"() {
        expect:
        facturaCuerpoBuilder.getTipo() == Factura.class
    }

    def "NegociacionCuerpoBuilder retorna clase Negociacion"() {
        expect:
        negociacionCuerpoBuilder.getTipo() == Negociacion.class
    }

    def "PersonaCuerpoBuilder retorna clase User"() {
        expect:
        personaCuerpoBuilder.getTipo() == User.class
    }
}