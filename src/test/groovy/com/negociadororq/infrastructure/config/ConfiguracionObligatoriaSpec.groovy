package com.negociadororq.infrastructure.config

import org.springframework.boot.SpringApplication
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import spock.lang.Specification

/**
 * El arranque falla antes de abrir el pool si la configuración obligatoria no llega por entorno, y el error nombra la
 * variable de entorno (no la propiedad ni el valor).
 */
class ConfiguracionObligatoriaSpec extends Specification {

    /** Como en application.yml: cada propiedad obligatoria apunta a su variable. */
    private static final Map<String, Object> YML = [
            'spring.datasource.url': '${DB_URL}',
            'spring.datasource.username': '${DB_USER}',
            'spring.datasource.password': '${DB_PASSWORD}',
            'security.service-credentials.ms-liquidador-neg.username': '${LIQUIDADOR_SERVICE_USERNAME}',
            'security.service-credentials.ms-liquidador-neg.password': '${LIQUIDADOR_SERVICE_PASSWORD_HASH}'
    ]

    /** Entorno completo y válido; los valores son de prueba y nunca deben aparecer en un error. */
    private static Map<String, Object> entornoValido() {
        [
                DB_URL: 'jdbc:mysql://bd-de-prueba:3306/esquema',
                DB_USER: 'usuario-de-prueba',
                DB_PASSWORD: 'valor-que-no-debe-salir',
                LIQUIDADOR_SERVICE_USERNAME: 'cuenta-de-prueba',
                LIQUIDADOR_SERVICE_PASSWORD_HASH: '$2a$10$' + 'x' * 53
        ]
    }

    private static StandardEnvironment entorno(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment()
        MutablePropertySources fuentes = environment.propertySources
        fuentes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        fuentes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
        fuentes.addLast(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables))
        fuentes.addLast(new MapPropertySource('application.yml', YML))
        environment
    }

    private static void sinValores(String mensaje) {
        entornoValido().values().each { assert !mensaje.contains(it as String) }
    }

    def "con todas las variables definidas el arranque continúa"() {
        when:
        ConfiguracionObligatoria.validar(entorno(entornoValido()))

        then:
        noExceptionThrown()
    }

    def "si falta #variable el error nombra la variable y no muestra valores"() {
        given:
        Map<String, Object> variables = entornoValido()
        variables.remove(variable)

        when:
        ConfiguracionObligatoria.validar(entorno(variables))

        then:
        IllegalStateException e = thrown()
        e.message.contains("falta la variable de entorno ${variable}")
        sinValores(e.message)

        where:
        variable << entornoValido().keySet().toList()
    }

    def "si #variable está en blanco el error la nombra como vacía"() {
        given:
        Map<String, Object> variables = entornoValido()
        variables.put(variable, '   ')

        when:
        ConfiguracionObligatoria.validar(entorno(variables))

        then:
        IllegalStateException e = thrown()
        e.message.contains("la variable de entorno ${variable} está vacía")
        sinValores(e.message)

        where:
        variable << entornoValido().keySet().toList()
    }

    def "si faltan varias variables el error las nombra todas"() {
        when:
        ConfiguracionObligatoria.validar(entorno([:]))

        then:
        IllegalStateException e = thrown()
        entornoValido().keySet().every { e.message.contains(it) }
    }

    def "una variable SPRING_DATASOURCE_PASSWORD en blanco, que sustituye al yml, se nombra a sí misma"() {
        given:
        Map<String, Object> variables = entornoValido()
        variables.put('SPRING_DATASOURCE_PASSWORD', '')

        when:
        ConfiguracionObligatoria.validar(entorno(variables))

        then:
        IllegalStateException e = thrown()
        e.message.contains('la variable de entorno SPRING_DATASOURCE_PASSWORD está vacía')
    }

    def "si #variable no es un hash bcrypt el error nombra la variable, no la propiedad ni el valor"() {
        given:
        Map<String, Object> variables = entornoValido()
        variables.put(variable, 'texto-plano-no-es-hash')

        when:
        ConfiguracionObligatoria.validar(entorno(variables))

        then:
        IllegalStateException e = thrown()
        e.message.contains("la variable de entorno ${variable} no es un hash bcrypt")
        !e.message.contains('service-credentials')
        !e.message.contains('texto-plano-no-es-hash')

        where:
        variable << ['LIQUIDADOR_SERVICE_PASSWORD_HASH']
    }

    def "arranque: sin la variable, SpringApplication falla antes de crear el contexto y la nombra"() {
        given:
        SpringApplication app = new SpringApplication(ContextoVacio)
        app.addListeners(new ConfiguracionObligatoria())

        when:
        app.run('--spring.main.web-application-type=none', '--spring.main.banner-mode=off',
                '--spring.datasource.password=${VARIABLE_DE_ARRANQUE_INEXISTENTE}')

        then:
        IllegalStateException e = thrown()
        e.message.contains('falta la variable de entorno VARIABLE_DE_ARRANQUE_INEXISTENTE')
    }

    def "la clase principal registra la validación al arrancar"() {
        expect:
        new File('src/main/java/com/negociadororq/MsNegociadorOrqApplication.java').text.contains('app.addListeners(new ConfiguracionObligatoria())')
    }

    @Configuration(proxyBeanMethods = false)
    static class ContextoVacio {
    }
}
