package com.negociadororq.infrastructure.security

import spock.lang.Specification

class SecretoHmacSpec extends Specification {

    private static String base64DeBytes(int n) {
        Base64.encoder.encodeToString(new byte[n])
    }

    def "rechaza el secreto al arrancar: #caso"() {
        when:
        SecretoHmac.decodificar("jwt.secret", "JWT_SECRET", valor)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains(mensaje)
        e.message.contains("JWT_SECRET")
        e.message.contains("48 a 63 bytes")
        e.cause == null

        where:
        caso                       | valor              || mensaje
        "variable ausente"         | null               || "Falta el secreto 'jwt.secret'"
        "valor vacío"              | ""                 || "Falta el secreto 'jwt.secret'"
        "placeholder sin resolver" | '${JWT_SECRET}'    || "quedó sin resolver"
        "Base64 inválido"          | "no-es-base64!!"   || "no es Base64 válido"
        "47 bytes"                 | base64DeBytes(47)  || "mide 47 bytes"
        "64 bytes"                 | base64DeBytes(64)  || "mide 64 bytes"
    }

    def "acepta un secreto Base64 de #n bytes"() {
        expect:
        SecretoHmac.decodificar("jwt.secret", "JWT_SECRET", base64DeBytes(n)).length == n

        where:
        n << [48, 63]
    }

    def "el mensaje de error no incluye el valor del secreto"() {
        given: "un Base64 válido de 47 bytes, distinguible en el mensaje"
        def valor = Base64.encoder.encodeToString(("x" * 47).bytes)

        when:
        SecretoHmac.decodificar("jwt.secret", "JWT_SECRET", valor)

        then:
        def e = thrown(IllegalStateException)
        !e.message.contains(valor)
    }
}
