package com.negociadororq.infrastructure.outbox;

import com.factoring.commons.auditoria.outbox.AuditoriaOutboxPort;
import com.factoring.commons.outbox.TipoDeEvento;
import com.factoring.commons.outbox.TiposDeEvento;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 28d (A2): tipos del outbox JDBC de ms-commons-util ({@code outbox_negociador}) además de la auditoría.
 *
 * <ul>
 *   <li>{@link #TIPO_NOTIFICACION_EMAIL}: los correos de la reversión (plantillas A y B), registrados en la MISMA
 *       transacción MySQL de la reversión o del paso a REVISION_MANUAL, con id de evento determinista. Salen al topic
 *       {@code notificacion.email} con el mismo formato que el {@code JsonSerializer} del outbox viejo (cabecera
 *       {@code __TypeId__} = la clase del evento), que es el que lee ms-notificaciones-util.</li>
 * </ul>
 *
 * <p>Como en ms-security-util (28c): el outbox de commons comprueba el esquema al crearse con su propio
 * {@code JdbcTemplate}; con Flyway activo debe crearse después de las migraciones.</p>
 */
@Configuration(proxyBeanMethods = false)
public class OutboxNegociadorConfig {

    /** Correo por el outbox JDBC (plantillas A y B de la reversión). */
    public static final String TIPO_NOTIFICACION_EMAIL = "negociador.notificacion-email";

    /** Topic de los correos (el mismo que consume ms-notificaciones-util). */
    public static final String TOPIC_NOTIFICACION_EMAIL = "${topics.notificacion-email}";

    /**
     * @return los tipos del outbox JDBC del negociador
     */
    @Bean
    public TiposDeEvento tiposDeEvento() {
        return TiposDeEvento.de(
                TipoDeEvento.de(TIPO_NOTIFICACION_EMAIL, NotificacionEmailEvent.class)
                        .topic(TOPIC_NOTIFICACION_EMAIL)
                        .clave(e -> "negociacion:" + e.getIdNegociacion())
                        .compatibleConJsonSerializer()
                        .build());
    }

    /** Con Flyway en el classpath, el outbox de commons se crea después de las migraciones. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    static class DespuesDeFlyway {

        @Bean
        static AbstractDependsOnBeanFactoryPostProcessor outboxNegociadorDespuesDeFlyway() {
            return new DependeDeFlyway(AuditoriaOutboxPort.class);
        }
    }

    /** {@code AuditoriaOutboxPort} depende de {@link FlywayMigrationInitializer}. */
    static final class DependeDeFlyway extends AbstractDependsOnBeanFactoryPostProcessor {
        DependeDeFlyway(final Class<?> tipo) {
            super(tipo, FlywayMigrationInitializer.class);
        }
    }
}
