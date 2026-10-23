package com.negociadororq.infrastructure.security

import com.negociadororq.application.service.LoginLockoutService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContextInitializer
import org.springframework.core.env.StandardEnvironment
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Cuenta de servicio de ms-liquidador-neg: usuario y hash bcrypt llegan por entorno, sin default.
 * Carga el application.yml real sin las variables del sistema, para que el resultado no dependa de la máquina.
 */
class CuentaServicioLiquidadorSpec extends Specification {

    private static final String USUARIO = 'ms-liquidador-neg'
    private static final String VAR_USUARIO = 'LIQUIDADOR_SERVICE_USERNAME'
    private static final String VAR_HASH = 'LIQUIDADOR_SERVICE_PASSWORD_HASH'
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder()

    @EnableConfigurationProperties(SecurityProperties)
    static class Config {
    }

    private static ApplicationContextRunner runner() {
        new ApplicationContextRunner()
                .withInitializer({ contexto ->
                    def fuentes = contexto.environment.propertySources
                    fuentes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
                    fuentes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
                } as ApplicationContextInitializer)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config)
    }

    private static String aleatoria() {
        UUID.randomUUID().toString()
    }

    private static List<String> mensajes(Throwable error) {
        def lista = []
        for (def causa = error; causa != null; causa = causa.cause) {
            lista << (causa.message ?: '')
        }
        lista
    }

    def "arranca con usuario y hash bcrypt por entorno y resuelve la cuenta por su usuario"() {
        given:
        def hash = ENCODER.encode(aleatoria())

        expect:
        runner().withPropertyValues("${VAR_USUARIO}=${USUARIO}".toString(), "${VAR_HASH}=${hash}".toString())
                .run { contexto ->
                    assert contexto.startupFailure == null
                    def cuenta = contexto.getBean(SecurityProperties).buscarPorUsuario(USUARIO)
                    assert cuenta.password == hash
                    assert cuenta.roles == ['OPERARIO']
                }
    }

    @Unroll
    def "no arranca si falta #variable"() {
        given:
        def valores = ["${VAR_USUARIO}=${USUARIO}".toString(), "${VAR_HASH}=${ENCODER.encode(aleatoria())}".toString()]
                .findAll { !it.startsWith(variable + '=') }

        expect:
        runner().withPropertyValues(valores as String[]).run { contexto ->
            assert contexto.startupFailure != null
            assert mensajes(contexto.startupFailure).any { it.contains(variable) }
        }

        where:
        variable << [VAR_USUARIO, VAR_HASH]
    }

    def "no arranca si el hash no tiene formato bcrypt, y el error no muestra el valor"() {
        given:
        def textoPlano = aleatoria()

        expect:
        runner().withPropertyValues("${VAR_USUARIO}=${USUARIO}".toString(), "${VAR_HASH}=${textoPlano}".toString())
                .run { contexto ->
                    assert contexto.startupFailure != null
                    def textos = mensajes(contexto.startupFailure)
                    assert textos.any { it.contains('security.service-credentials.ms-liquidador-neg.password') }
                    assert textos.every { !it.contains(textoPlano) }
                }
    }

    def "no arranca si el usuario está vacío"() {
        expect:
        runner().withPropertyValues("${VAR_USUARIO}=".toString(), "${VAR_HASH}=${ENCODER.encode(aleatoria())}".toString())
                .run { contexto ->
                    assert contexto.startupFailure != null
                    assert mensajes(contexto.startupFailure)
                            .any { it.contains('security.service-credentials.ms-liquidador-neg.username') }
                }
    }

    def "tras rotar el hash, la contraseña vieja se rechaza y la nueva se acepta"() {
        given:
        def vieja = aleatoria()
        def nueva = aleatoria()
        def properties = new SecurityProperties()
        def lockout = Stub(LoginLockoutService) { isLocked(_) >> false }
        def provider = new DaoAuthenticationProvider()
        provider.userDetailsService = new CompositeUserDetailsService(properties, Stub(DatabaseUserDetailsService), lockout)
        provider.passwordEncoder = ENCODER

        when: 'antes de rotar'
        properties.serviceCredentials = [(USUARIO): credencial(ENCODER.encode(vieja))]

        then:
        provider.authenticate(new UsernamePasswordAuthenticationToken(USUARIO, vieja)).authenticated

        when: 'se despliega el hash de la contraseña nueva'
        properties.serviceCredentials = [(USUARIO): credencial(ENCODER.encode(nueva))]

        then:
        provider.authenticate(new UsernamePasswordAuthenticationToken(USUARIO, nueva)).authenticated

        when: 'la contraseña vieja'
        provider.authenticate(new UsernamePasswordAuthenticationToken(USUARIO, vieja))

        then:
        thrown(BadCredentialsException)

        when: 'sin contraseña'
        provider.authenticate(new UsernamePasswordAuthenticationToken(USUARIO, ''))

        then:
        thrown(BadCredentialsException)
    }

    private static SecurityProperties.ServiceCredential credencial(String hash) {
        def credential = new SecurityProperties.ServiceCredential()
        credential.username = USUARIO
        credential.password = hash
        credential.roles = ['OPERARIO']
        credential
    }
}
