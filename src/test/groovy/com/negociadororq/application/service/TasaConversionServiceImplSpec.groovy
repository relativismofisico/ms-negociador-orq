package com.negociadororq.application.service

import com.negociadororq.application.exception.PeticionInvalidaException
import spock.lang.Specification
import spock.lang.Unroll

import java.math.BigDecimal

class TasaConversionServiceImplSpec extends Specification {

    TasaConversionServiceImpl service = new TasaConversionServiceImpl()

    def "tasa mes vencido de 2% equivale a tasa mes anticipado de 1.9608% (formula exacta, sin exponentes)"() {
        when:
        def resultado = service.convertir(BigDecimal.valueOf(2), "TASA_MES_VENCIDO")

        then:
        resultado.tasaMesVencido == 2.0000
        resultado.tasaMesAnticipado == 1.9608
    }

    def "tasa mes vencido de 2% equivale a tasa efectiva anual de aproximadamente 26.82%"() {
        when:
        def resultado = service.convertir(BigDecimal.valueOf(2), "TASA_MES_VENCIDO")

        then:
        Math.abs(resultado.tasaEfectivaAnual.doubleValue() - 26.8242) < 0.01
    }

    def "el valor original se refleja tal cual en el campo correspondiente a su propio tipo"() {
        when:
        def resultadoVencido = service.convertir(BigDecimal.valueOf(2.1), "TASA_MES_VENCIDO")
        def resultadoAnual = service.convertir(BigDecimal.valueOf(28.32), "TASA_EFECTIVA_ANUAL")
        def resultadoAnticipado = service.convertir(BigDecimal.valueOf(1.96), "TASA_MES_ANTICIPADO")

        then:
        resultadoVencido.tasaMesVencido == 2.1000
        resultadoAnual.tasaEfectivaAnual == 28.3200
        resultadoAnticipado.tasaMesAnticipado == 1.9600
    }

    @Unroll
    def "convertir de #tipoOrigen a #tipoOrigen y de vuelta reproduce el valor original (round-trip): #valorOriginal"() {
        given:
        def primeraConversion = service.convertir(BigDecimal.valueOf(valorOriginal), tipoOrigen)
        def valorIntermedio = primeraConversion."${campoIntermedio}" as BigDecimal

        when:
        def segundaConversion = service.convertir(valorIntermedio, tipoIntermedio)
        def valorFinal = segundaConversion."${campoOrigen}" as BigDecimal

        then:
        Math.abs(valorFinal.doubleValue() - valorOriginal) < 0.001

        where:
        tipoOrigen               | campoOrigen         | tipoIntermedio            | campoIntermedio      | valorOriginal
        "TASA_MES_VENCIDO"       | "tasaMesVencido"    | "TASA_EFECTIVA_ANUAL"     | "tasaEfectivaAnual"  | 2.1d
        "TASA_MES_VENCIDO"       | "tasaMesVencido"    | "TASA_MES_ANTICIPADO"     | "tasaMesAnticipado"  | 2.1d
        "TASA_EFECTIVA_ANUAL"    | "tasaEfectivaAnual" | "TASA_MES_VENCIDO"        | "tasaMesVencido"     | 30.0d
        "TASA_EFECTIVA_ANUAL"    | "tasaEfectivaAnual" | "TASA_MES_ANTICIPADO"     | "tasaMesAnticipado"  | 30.0d
        "TASA_MES_ANTICIPADO"    | "tasaMesAnticipado" | "TASA_MES_VENCIDO"        | "tasaMesVencido"     | 1.8d
        "TASA_MES_ANTICIPADO"    | "tasaMesAnticipado" | "TASA_EFECTIVA_ANUAL"     | "tasaEfectivaAnual"  | 1.8d
    }

    def "lanza PeticionInvalidaException si el valor es null"() {
        when:
        service.convertir(null, "TASA_MES_VENCIDO")

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.message.contains("obligatorio")
    }

    def "lanza PeticionInvalidaException si el valor es negativo"() {
        when:
        service.convertir(BigDecimal.valueOf(-1), "TASA_MES_VENCIDO")

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.message.contains("negativo")
    }

    def "lanza PeticionInvalidaException si el tipo de tasa es null"() {
        when:
        service.convertir(BigDecimal.valueOf(2), null)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.message.contains("obligatorio")
    }

    def "lanza PeticionInvalidaException si el tipo de tasa no es uno de los soportados"() {
        when:
        service.convertir(BigDecimal.valueOf(2), "TASA_INVENTADA")

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.message.contains("no soportado")
    }

    def "lanza PeticionInvalidaException si la tasa mes anticipado es mayor o igual a 100%"() {
        when:
        service.convertir(BigDecimal.valueOf(100), "TASA_MES_ANTICIPADO")

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.message.contains("menor a 100")
    }
}
