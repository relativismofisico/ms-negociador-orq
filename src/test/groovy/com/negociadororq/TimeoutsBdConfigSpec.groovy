package com.negociadororq

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource
import spock.lang.Specification

/**
 * El driver MySQL tiene connect timeout y socket timeout configurables por entorno. Sin socketTimeout, una BD que deja
 * de responder sin cerrar la conexión dejaba la petición colgada: connection-timeout de Hikari solo acota la espera
 * de una conexión libre del pool.
 *
 * <p>Lee el application.yml de main por ruta: el classpath de test puede tener su propio application.yml, que lo tapa.
 */
class TimeoutsBdConfigSpec extends Specification {

    private static final String PREFIJO = 'spring.datasource.hikari.data-source-properties.'

    private static ConfigurableEnvironment entorno(Map<String, Object> variables) {
        def env = new StandardEnvironment()
        env.propertySources.addFirst(new MapPropertySource('variables-de-entorno', variables))
        def yml = new FileSystemResource('src/main/resources/application.yml')
        // Bucle y no .each: el closure de Groovy es Serializable sin serialVersionUID (SpotBugs SE_NO_SERIALVERSIONID)
        for (def fuente : new YamlPropertySourceLoader().load('application', yml)) {
            env.propertySources.addLast(fuente)
        }
        env
    }

    def "por defecto: 10 s para conectar y 30 s por lectura"() {
        given:
        def env = entorno([:])

        expect:
        env.getProperty(PREFIJO + 'connectTimeout') == '10000'
        env.getProperty(PREFIJO + 'socketTimeout') == '30000'
    }

    def "se configuran con DB_CONNECT_TIMEOUT_MS y DB_SOCKET_TIMEOUT_MS"() {
        given:
        def env = entorno([DB_CONNECT_TIMEOUT_MS: '3000', DB_SOCKET_TIMEOUT_MS: '5000'])

        expect:
        env.getProperty(PREFIJO + 'connectTimeout') == '3000'
        env.getProperty(PREFIJO + 'socketTimeout') == '5000'
    }
}
