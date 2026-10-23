package com.negociadororq

import spock.lang.Specification

/**
 * Ningún application*.yml trae URL, host, usuario ni contraseña de la base de datos como valor por defecto: sin la
 * variable de entorno el servicio no arranca, en vez de conectarse en silencio a una base compartida
 * (ms-security-util#52).
 */
class SinDefaultsDeBaseDeDatosSpec extends Specification {

    def "#archivo no trae defaults de URL, host, usuario ni contraseña de BD"() {
        expect:
        !(new File("src/main/resources/${archivo}").text =~ /\$\{DB_(URL|USERNAME|USER|PASSWORD|HOST):/)

        where:
        archivo << ymls()
    }

    def "#archivo no trae URL, usuario ni contraseña de BD literales (solo variables)"() {
        given:
        def documentos = new org.yaml.snakeyaml.Yaml().loadAll(new File("src/main/resources/${archivo}").text)
                .findAll { it instanceof Map }

        expect:
        documentos.every { Map documento ->
            Map datasource = (documento.spring instanceof Map && documento.spring.datasource instanceof Map) ?
                    documento.spring.datasource as Map : [:]
            ['url', 'username', 'password'].every { clave ->
                datasource[clave] == null || datasource[clave].toString().contains('${')
            }
        }

        where:
        archivo << ymls()
    }

    def "application.yml declara las variables de BD sin default"() {
        given:
        String yml = new File('src/main/resources/application.yml').text

        expect:
        yml.contains('${DB_HOST}')
        yml.contains('${DB_PASSWORD}')
        yml.contains('${DB_USER}')
    }

    private static List<String> ymls() {
        List<String> archivos = []
        for (String nombre : new File('src/main/resources').list()) {
            if (nombre ==~ /application.*\.ya?ml/) {
                archivos << nombre
            }
        }
        archivos.sort()
    }
}
