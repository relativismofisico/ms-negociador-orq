package com.negociadororq

import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.HealthEndpointGroups
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.FileSystemResource
import spock.lang.Specification

/**
 * Grupos de salud de application.yml con los indicadores reales sustituidos por dobles: liveness solo mira el estado de
 * la aplicación (una dependencia caída no debe reiniciar el contenedor) y readiness saca la instancia del tráfico si
 * cae la BD, MongoDB o Redis.
 */
class GruposSaludConfigSpec extends Specification {

    private static final List<String> DEPENDENCIAS = ['db', 'mongo', 'redis']

    def "liveness solo incluye livenessState y readiness incluye readinessState, db, mongo y redis"() {
        expect:
        contexto(null).run { ctx ->
            def grupos = ctx.getBean(HealthEndpointGroups)
            assert grupos.get('liveness').isMember('livenessState')
            assert !grupos.get('liveness').isMember('readinessState')
            DEPENDENCIAS.each { assert !grupos.get('liveness').isMember(it) }
            assert grupos.get('readiness').isMember('readinessState')
            DEPENDENCIAS.each { assert grupos.get('readiness').isMember(it) }
        }
    }

    def "con todo disponible, liveness y readiness UP"() {
        expect:
        contexto(null).run { ctx ->
            assert estado(ctx, 'liveness') == Status.UP
            assert estado(ctx, 'readiness') == Status.UP
        }
    }

    def "con #caida caído: liveness sigue UP y readiness DOWN"() {
        expect:
        contexto(caida).run { ctx ->
            assert estado(ctx, 'liveness') == Status.UP
            assert estado(ctx, 'readiness') == Status.DOWN
        }

        where:
        caida << DEPENDENCIAS
    }

    private static ApplicationContextRunner contexto(String caida) {
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApplicationAvailabilityAutoConfiguration,
                        AvailabilityHealthContributorAutoConfiguration, AvailabilityProbesAutoConfiguration,
                        HealthContributorAutoConfiguration, HealthEndpointAutoConfiguration))
                .withPropertyValues(propiedadesManagement())
        for (String nombre : DEPENDENCIAS) {
            runner = runner.withBean(nombre + 'HealthIndicator', IndicadorFijo, nombre == caida)
        }
        runner
    }

    private static Status estado(ApplicationContext ctx, String grupo) {
        AvailabilityChangeEvent.publish(ctx, LivenessState.CORRECT)
        AvailabilityChangeEvent.publish(ctx, ReadinessState.ACCEPTING_TRAFFIC)
        ctx.getBean(HealthEndpoint).healthForPath(grupo).status
    }

    /** Las propiedades {@code management.*} tal como están en src/main/resources/application.yml. */
    private static String[] propiedadesManagement() {
        List<String> propiedades = []
        def fuentes = new YamlPropertySourceLoader().load('application',
                new FileSystemResource('src/main/resources/application.yml'))
        for (def fuente : fuentes) {
            for (String nombre : ((EnumerablePropertySource) fuente).propertyNames) {
                if (nombre.startsWith('management.')) {
                    propiedades.add(nombre + '=' + fuente.getProperty(nombre))
                }
            }
        }
        propiedades as String[]
    }

    /** Doble del indicador real (db, mongo, redis): responde siempre UP o siempre DOWN. */
    static class IndicadorFijo implements HealthIndicator {
        private final boolean caido

        IndicadorFijo(boolean caido) {
            this.caido = caido
        }

        @Override
        Health health() {
            caido ? Health.down().build() : Health.up().build()
        }
    }
}
