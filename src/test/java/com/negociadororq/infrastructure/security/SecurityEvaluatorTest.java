package com.negociadororq.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SecurityEvaluator — lógica de autorización por grupos de roles")
class SecurityEvaluatorTest {

    private SecurityEvaluator evaluator;
    private SecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setRoles(Map.of(
                "negociacion", List.of("ADMINISTRADOR", "EMPRESA", "OPERARIO"),
                "endoso",      List.of("ADMINISTRADOR", "EMPRESA", "FONDEADOR"),
                "otp",         List.of("ADMINISTRADOR", "EMPRESA", "FONDEADOR"),
                "validacion",  List.of("ADMINISTRADOR", "OPERARIO", "EMPRESA", "FONDEADOR")
        ));
        evaluator = new SecurityEvaluator(properties);
    }

    @Test
    @DisplayName("Retorna true cuando el rol del usuario está en el grupo")
    void tieneRol_verdadero() {
        Authentication auth = buildAuth("EMPRESA");

        assertThat(evaluator.tieneRol(auth, "negociacion")).isTrue();
        assertThat(evaluator.tieneRol(auth, "endoso")).isTrue();
    }

    @Test
    @DisplayName("Retorna false cuando el rol del usuario NO está en el grupo")
    void tieneRol_falso() {
        Authentication auth = buildAuth("FONDEADOR");

        assertThat(evaluator.tieneRol(auth, "negociacion")).isFalse();
    }

    @Test
    @DisplayName("ADMINISTRADOR tiene acceso a todos los grupos")
    void administradorTieneAccesoTotal() {
        Authentication auth = buildAuth("ADMINISTRADOR");

        assertThat(evaluator.tieneRol(auth, "negociacion")).isTrue();
        assertThat(evaluator.tieneRol(auth, "endoso")).isTrue();
        assertThat(evaluator.tieneRol(auth, "otp")).isTrue();
        assertThat(evaluator.tieneRol(auth, "validacion")).isTrue();
    }

    @Test
    @DisplayName("Grupo inexistente en YAML retorna false")
    void grupoInexistente_retornaFalso() {
        Authentication auth = buildAuth("ADMINISTRADOR");

        assertThat(evaluator.tieneRol(auth, "grupo-que-no-existe")).isFalse();
    }

    @Test
    @DisplayName("Authentication nula retorna false (seguro por defecto)")
    void authenticationNula_retornaFalso() {
        assertThat(evaluator.tieneRol(null, "negociacion")).isFalse();
    }

    @Test
    @DisplayName("Authentication no autenticada retorna false")
    void noAutenticado_retornaFalso() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertThat(evaluator.tieneRol(auth, "negociacion")).isFalse();
    }

    @Test
    @DisplayName("FONDEADOR puede hacer endoso y OTP pero no negociación")
    void fondeadorPermisosParciales() {
        Authentication auth = buildAuth("FONDEADOR");

        assertThat(evaluator.tieneRol(auth, "negociacion")).isFalse();
        assertThat(evaluator.tieneRol(auth, "endoso")).isTrue();
        assertThat(evaluator.tieneRol(auth, "otp")).isTrue();
        assertThat(evaluator.tieneRol(auth, "validacion")).isTrue();
    }

    @Test
    @DisplayName("OPERARIO puede negociar y validar pero no hacer endoso")
    void operarioPermisosParciales() {
        Authentication auth = buildAuth("OPERARIO");

        assertThat(evaluator.tieneRol(auth, "negociacion")).isTrue();
        assertThat(evaluator.tieneRol(auth, "endoso")).isFalse();
        assertThat(evaluator.tieneRol(auth, "otp")).isFalse();
        assertThat(evaluator.tieneRol(auth, "validacion")).isTrue();
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Authentication buildAuth(String rol) {
        return new AbstractAuthenticationToken(List.of(new SimpleGrantedAuthority(rol))) {
            @Override
            public Object getCredentials() {
                return null;
            }
            @Override
            public Object getPrincipal() {
                return "usuario-test";
            }
            { setAuthenticated(true); }
        };
    }
}
