package com.negociadororq.infrastructure.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import spock.lang.Specification

import java.time.Duration

class LoginLockoutServiceImplSpec extends Specification {

    static final int MAX_ATTEMPTS = 5
    static final long WINDOW_MINUTES = 15L
    static final String USERNAME = "user@test.com"
    static final String KEY = "login-lockout:negociador-orq:" + USERNAME

    def redisTemplate = Mock(StringRedisTemplate)
    def valueOperations = Mock(ValueOperations)

    def service = new LoginLockoutServiceImpl(redisTemplate, MAX_ATTEMPTS, WINDOW_MINUTES)

    def "isLocked devuelve false cuando no hay intentos registrados"() {
        given:
        redisTemplate.opsForValue() >> valueOperations
        valueOperations.get(KEY) >> null

        expect:
        !service.isLocked(USERNAME)
    }

    def "isLocked devuelve false cuando los intentos estan por debajo del limite"() {
        given:
        redisTemplate.opsForValue() >> valueOperations
        valueOperations.get(KEY) >> "4"

        expect:
        !service.isLocked(USERNAME)
    }

    def "isLocked devuelve true cuando los intentos alcanzan el limite"() {
        given:
        redisTemplate.opsForValue() >> valueOperations
        valueOperations.get(KEY) >> "5"

        expect:
        service.isLocked(USERNAME)
    }

    def "registerFailure incrementa el contador y setea el TTL en el primer intento"() {
        given:
        redisTemplate.opsForValue() >> valueOperations
        valueOperations.increment(KEY) >> 1L

        when:
        service.registerFailure(USERNAME)

        then:
        1 * redisTemplate.expire(KEY, Duration.ofMinutes(WINDOW_MINUTES))
    }

    def "registerFailure no vuelve a setear el TTL en intentos siguientes"() {
        given:
        redisTemplate.opsForValue() >> valueOperations
        valueOperations.increment(KEY) >> 3L

        when:
        service.registerFailure(USERNAME)

        then:
        0 * redisTemplate.expire(_, _)
    }

    def "reset elimina el contador del usuario"() {
        when:
        service.reset(USERNAME)

        then:
        1 * redisTemplate.delete(KEY)
    }
}
