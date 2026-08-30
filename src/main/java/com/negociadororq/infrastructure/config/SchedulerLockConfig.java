package com.negociadororq.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Lock distribuido (Redis) para que los {@code @Scheduled} corran una sola
 * vez aunque haya varias instancias de este servicio corriendo a la vez
 * detrás del load balancer.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
public class SchedulerLockConfig {

    /**
     * Registra Redis como backend del lock, namespaceado por servicio para
     * no colisionar con los locks de otros microservicios que comparten el
     * mismo Redis.
     *
     * @param connectionFactory conexión a Redis ya configurada por Spring Data Redis
     * @return proveedor de lock distribuido
     */
    @Bean
    public LockProvider lockProvider(final RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "ms-negociador-orq");
    }
}
