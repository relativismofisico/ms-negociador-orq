package com.negociadororq.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;

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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            // Sin estado — JWT es el único mecanismo de sesión
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF innecesario en APIs stateless con JWT
            .csrf(AbstractHttpConfigurer::disable)

            // Form login deshabilitado — JWT + Basic Auth para servicio-a-servicio
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(Customizer.withDefaults())

            .authorizeHttpRequests(authorization -> authorization
                // Swagger/OpenAPI disponible sin token (ajustar por perfil si se requiere)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()
                // Todo lo demás requiere un JWT válido; @PreAuthorize gestiona la autorización fina
                .anyRequest().authenticated()
            )

            // OAuth2 Resource Server — valida el JWT en cada petición
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtRolConverter)
                )
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(ObjectMapper objectMapper) {
        return new JjwtDecoder(securityProperties.getJwt().getSecret(), objectMapper);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DatabaseUserDetailsService databaseUserDetailsService(UserRepository userRepository) {
        return new DatabaseUserDetailsService(userRepository);
    }

    @Bean
    public UserDetailsService userDetailsService(
            SecurityProperties securityProperties,
            DatabaseUserDetailsService databaseUserDetailsService) {
        return new CompositeUserDetailsService(securityProperties, databaseUserDetailsService);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService userDetailsService,
            BCryptPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
            .authenticationProvider(daoAuthenticationProvider)
            .build();
    }
}

