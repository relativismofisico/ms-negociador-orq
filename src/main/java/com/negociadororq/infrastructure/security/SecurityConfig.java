package com.negociadororq.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración central de Spring Security 6 para ms-negociador-orq.
 *
 * Estrategia:
 * - Stateless (sin sesiones HTTP).
 * - OAuth2 Resource Server con JWT de clave simétrica HMAC.
 * - Autorización por roles externalizada en application.yml via @PreAuthorize.
 * - Todos los endpoints requieren autenticación; la autorización fina se delega a los controllers.
 *
 * Documentación abierta (Swagger UI) disponible sin token para entornos no-prod.
 * En producción se recomienda agregar swagger paths a la lista de exclusión solo en dev/qa.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtRolConverter jwtRolConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Sin estado — JWT es el único mecanismo de sesión
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF innecesario en APIs stateless con JWT
            .csrf(AbstractHttpConfigurer::disable)

            // Form login y basic auth deshabilitados — solo JWT
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                // Swagger/OpenAPI disponible sin token (ajustar por perfil si se requiere)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                // Todo lo demás requiere un JWT válido; @PreAuthorize gestiona la autorización fina
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server — valida el JWT en cada petición
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtRolConverter)
                )
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    /**
     * Decoder basado en jjwt — garantiza compatibilidad con ms-security.
     * Reutiliza la misma clave y librería que el generador del token.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return new JjwtDecoder(securityProperties.getJwt().getSecret(), mapper);
    }
}