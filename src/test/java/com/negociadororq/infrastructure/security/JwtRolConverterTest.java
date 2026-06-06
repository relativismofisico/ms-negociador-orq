package com.negociadororq.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtRolConverter — conversión de claim 'rol' a GrantedAuthority")
class JwtRolConverterTest {

    private JwtRolConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JwtRolConverter();
    }

    @Test
    @DisplayName("Convierte claim 'rol' a GrantedAuthority sin prefijo ROLE_")
    void convertirRolSimple() {
        Jwt jwt = buildJwt(Map.of("rol", "ADMINISTRADOR"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("Cada rol válido se mapea correctamente (EMPRESA, OPERARIO, FONDEADOR)")
    void convertirCadaRolValido() {
        for (String rol : new String[]{"EMPRESA", "OPERARIO", "FONDEADOR"}) {
            Jwt jwt = buildJwt(Map.of("rol", rol));
            JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);
            assertThat(token.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly(rol);
        }
    }

    @Test
    @DisplayName("JWT sin claim 'rol' produce cero GrantedAuthorities")
    void sinClaimRol_sinAutoridades() {
        Jwt jwt = buildJwt(Map.of("sub", "user-sin-rol"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Claim 'rol' vacío produce cero GrantedAuthorities")
    void claimRolVacio_sinAutoridades() {
        Jwt jwt = buildJwt(Map.of("rol", ""));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("El subject del JWT se usa como nombre de autenticación")
    void subjectComoNombreAutenticacion() {
        Jwt jwt = buildJwt(Map.of("rol", "OPERARIO", "sub", "12345678-9"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("12345678-9");
    }

    @Test
    @DisplayName("El rol NO tiene prefijo ROLE_ — usa hasAuthority(), no hasRole()")
    void sinPrefixRolePrefijo() {
        Jwt jwt = buildJwt(Map.of("rol", "FONDEADOR"));

        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.startsWith("ROLE_"));
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Jwt buildJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("fake-token")
                .header("alg", "HS384")
                .header("typ", "JWT")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}