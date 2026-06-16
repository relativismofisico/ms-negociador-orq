package com.negociadororq.application.util

import jakarta.servlet.http.HttpServletRequest
import spock.lang.Specification

class IpUtilsSpec extends Specification {

    def "retorna primera IP de X-Forwarded-For cuando es valida"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> "192.168.1.1, 10.0.0.1"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "192.168.1.1"
    }

    def "retorna IP de X-Real-IP cuando X-Forwarded-For es null"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> null
        request.getHeader("X-Real-IP") >> "10.0.0.50"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "10.0.0.50"
    }

    def "retorna IP de X-Real-IP cuando X-Forwarded-For esta vacio"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> ""
        request.getHeader("X-Real-IP") >> "172.16.0.1"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "172.16.0.1"
    }

    def "retorna IP de X-Real-IP cuando X-Forwarded-For es 'unknown'"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> "unknown"
        request.getHeader("X-Real-IP") >> "172.16.0.2"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "172.16.0.2"
    }

    def "retorna RemoteAddr cuando ambos headers son null"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> null
        request.getHeader("X-Real-IP") >> null
        request.getRemoteAddr() >> "127.0.0.1"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "127.0.0.1"
    }

    def "retorna RemoteAddr cuando X-Real-IP es null"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> ""
        request.getHeader("X-Real-IP") >> null
        request.getRemoteAddr() >> "192.168.0.100"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "192.168.0.100"
    }

    def "retorna RemoteAddr cuando X-Real-IP es vacio"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> null
        request.getHeader("X-Real-IP") >> ""
        request.getRemoteAddr() >> "10.10.10.10"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "10.10.10.10"
    }

    def "retorna RemoteAddr cuando X-Real-IP es 'unknown'"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> null
        request.getHeader("X-Real-IP") >> "unknown"
        request.getRemoteAddr() >> "10.10.10.11"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "10.10.10.11"
    }

    def "X-Forwarded-For con un solo IP devuelve ese IP"() {
        given:
        def request = Mock(HttpServletRequest)
        request.getHeader("X-Forwarded-For") >> "203.0.113.5"

        when:
        def ip = IpUtils.obtenerIp(request)

        then:
        ip == "203.0.113.5"
    }
}