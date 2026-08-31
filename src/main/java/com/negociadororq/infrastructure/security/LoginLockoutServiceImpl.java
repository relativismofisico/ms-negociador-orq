package com.negociadororq.infrastructure.security;

import java.time.Duration;

import com.negociadororq.application.service.LoginLockoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Implementación del bloqueo por intentos fallidos respaldada por Redis:
 * un contador por username con TTL, incrementado en cada fallo (ver
 * {@link LoginAttemptListener}) y reseteado en una autenticación exitosa.
 */
@Service
@Slf4j
public class LoginLockoutServiceImpl implements LoginLockoutService {

    private static final String KEY_PREFIX = "login-lockout:negociador-orq:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration lockoutWindow;

    /**
     * Construye el servicio con el template de Redis y los límites configurados.
     *
     * @param redisTemplate template de Redis para operaciones String
     * @param maxAttempts   cantidad de intentos fallidos que activan el bloqueo
     * @param windowMinutes minutos que dura el bloqueo (y la ventana de conteo)
     */
    public LoginLockoutServiceImpl(final StringRedisTemplate redisTemplate,
            @Value("${login-lockout.max-attempts:5}") final int maxAttempts,
            @Value("${login-lockout.window-minutes:15}") final long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.lockoutWindow = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public boolean isLocked(final String username) {
        final String value = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        return value != null && Long.parseLong(value) >= maxAttempts;
    }

    @Override
    public void registerFailure(final String username) {
        final String key = KEY_PREFIX + username;
        final Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, lockoutWindow);
        }
        if (attempts != null && attempts >= maxAttempts) {
            log.warn("[LoginLockoutServiceImpl][registerFailure][ms-negociador-orq] Usuario {} alcanzo el limite de "
                    + "{} intentos fallidos, bloqueado por {} minutos", username, maxAttempts,
                    lockoutWindow.toMinutes());
        }
    }

    @Override
    public void reset(final String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }
}
