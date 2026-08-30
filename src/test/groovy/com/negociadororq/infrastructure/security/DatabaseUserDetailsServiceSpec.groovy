package com.negociadororq.infrastructure.security

import com.negociadororq.application.service.LoginLockoutService
import com.negociadororq.infrastructure.persistence.entity.UserEntity
import com.negociadororq.infrastructure.persistence.repository.UserRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import spock.lang.Specification

class DatabaseUserDetailsServiceSpec extends Specification {

    def userRepository = Mock(UserRepository)
    def loginLockoutService = Mock(LoginLockoutService)

    def service = new DatabaseUserDetailsService(userRepository, loginLockoutService)

    private UserEntity buildEntity(String username = "operario1", long stateUserId = 1L) {
        UserEntity.builder()
                .username(username)
                .password("encoded")
                .stateUserId(stateUserId)
                .build()
    }

    def "loadUserByUsername lanza UsernameNotFoundException cuando el usuario no existe"() {
        given:
        userRepository.findByUsername("ghost") >> Optional.empty()

        when:
        service.loadUserByUsername("ghost")

        then:
        thrown(UsernameNotFoundException)
    }

    def "loadUserByUsername no esta bloqueado cuando LoginLockoutService dice que no"() {
        given:
        userRepository.findByUsername("operario1") >> Optional.of(buildEntity())
        loginLockoutService.isLocked("operario1") >> false

        when:
        def result = service.loadUserByUsername("operario1")

        then:
        result.isAccountNonLocked()
    }

    def "loadUserByUsername esta bloqueado cuando LoginLockoutService dice que si"() {
        given:
        userRepository.findByUsername("operario1") >> Optional.of(buildEntity())
        loginLockoutService.isLocked("operario1") >> true

        when:
        def result = service.loadUserByUsername("operario1")

        then:
        !result.isAccountNonLocked()
    }

    def "loadUserByUsername deshabilita la cuenta cuando stateUserId no es 1"() {
        given:
        userRepository.findByUsername("operario1") >> Optional.of(buildEntity("operario1", 2L))
        loginLockoutService.isLocked("operario1") >> false

        when:
        def result = service.loadUserByUsername("operario1")

        then:
        !result.isEnabled()
    }
}
