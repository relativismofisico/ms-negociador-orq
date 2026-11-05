package com.negociadororq

import spock.lang.Specification

/**
 * Hikari valida cada conexión al prestarla (aliveBypassWindowMs = 0): con la BD sin responder, la primera petición
 * no espera el socketTimeout entero (30 s) antes del 503.
 */
class ValidacionConexionesBdSpec extends Specification {

    String previo

    def setup() {
        previo = System.getProperty(ValidacionConexionesBd.PROPIEDAD)
        System.clearProperty(ValidacionConexionesBd.PROPIEDAD)
    }

    def cleanup() {
        if (previo == null) {
            System.clearProperty(ValidacionConexionesBd.PROPIEDAD)
        } else {
            System.setProperty(ValidacionConexionesBd.PROPIEDAD, previo)
        }
    }

    def "sin valor explícito, fija la ventana de Hikari a 0: valida cada conexión al prestarla"() {
        when:
        ValidacionConexionesBd.validarSiempreAlPrestar()

        then:
        ValidacionConexionesBd.PROPIEDAD == 'com.zaxxer.hikari.aliveBypassWindowMs'
        System.getProperty(ValidacionConexionesBd.PROPIEDAD) == '0'
    }

    def "un valor explícito en la JVM se respeta"() {
        given:
        System.setProperty(ValidacionConexionesBd.PROPIEDAD, '250')

        when:
        ValidacionConexionesBd.validarSiempreAlPrestar()

        then:
        System.getProperty(ValidacionConexionesBd.PROPIEDAD) == '250'
    }

    def "main la fija antes de arrancar el contexto"() {
        given:
        def fuente = new File('src/main/java/com/negociadororq/MsNegociadorOrqApplication.java').getText('UTF-8')
        def cuerpoMain = fuente.substring(fuente.indexOf('void main('))

        expect:
        cuerpoMain.indexOf('ValidacionConexionesBd.validarSiempreAlPrestar();') >= 0
        cuerpoMain.indexOf('ValidacionConexionesBd.validarSiempreAlPrestar();') < cuerpoMain.indexOf('SpringApplication')
    }
}
