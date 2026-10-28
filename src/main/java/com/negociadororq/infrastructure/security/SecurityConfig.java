package com.negociadororq.infrastructure.security;

import java.util.Arrays;
import java.util.List;

import com.factoring.commons.web.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.service.LoginLockoutService;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            // Sin estado — JWT es el único mecanismo de sesión
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF innecesario en APIs stateless con JWT
            .csrf(AbstractHttpConfigurer::disable)

            // Habilita el preflight CORS (OPTIONS) antes de la cadena de autenticación,
            // necesario desde que hay front-ends que llaman a este servicio directamente
            // desde el navegador (ej: cupo propio de fondeo).
            .cors(Customizer.withDefaults())

            // Form login deshabilitado — JWT + Basic Auth para servicio-a-servicio
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))

            .authorizeHttpRequests(authorization -> authorization
                // Swagger/OpenAPI disponible sin token (ajustar por perfil si se requiere)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**",
                    // Reenvío de errores del contenedor: sin esto, un 500 de cualquier endpoint se reenvía a /error,
                    // cae en anyRequest().authenticated() y el cliente recibe un 401 (ms-security-util#66).
                    "/error"
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
            )

            // ExceptionTranslationFilter usa los mismos handlers para errores 401/403
            // que no provienen del BearerTokenAuthenticationFilter (ej: sin header, BasicAuth)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            // BD caída al comprobar el usuario del token (JjwtDecoder): 503, no 401.
            .addFilterBefore(new ValidacionTokenNoDisponibleFilter(authenticationEntryPoint),
                BearerTokenAuthenticationFilter.class)

            // Límite de solicitudes por IP lo más temprano posible en la cadena,
            // antes de intentar Basic Auth o validar el Bearer JWT.
            .addFilterBefore(rateLimitFilter, SecurityContextHolderFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(ObjectMapper objectMapper, UserRepository userRepository) {
        return new JjwtDecoder(securityProperties.getJwt().getSecret(), objectMapper, userRepository);
    }

    /**
     * Única fuente de CORS de la app — mismo patrón que {@code ms-loadfiles-neg}. A diferencia
     * de repos con uso amplio, este servicio solo lo llama {@code ms-operator-front-neg}
     * (puerto 4800): el default se restringe a ese único origen en vez de habilitar los 8
     * puertos genéricos de la plataforma. Override real por ambiente vía
     * {@code cors.allowed-origins} ({@code CORS_ALLOWED_ORIGINS} en qa/prod).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:4800}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DatabaseUserDetailsService databaseUserDetailsService(
            UserRepository userRepository,
            LoginLockoutService loginLockoutService) {
        return new DatabaseUserDetailsService(userRepository, loginLockoutService);
    }

    @Bean
    public UserDetailsService userDetailsService(
            SecurityProperties securityProperties,
            DatabaseUserDetailsService databaseUserDetailsService,
            LoginLockoutService loginLockoutService) {
        return new CompositeUserDetailsService(securityProperties, databaseUserDetailsService, loginLockoutService);
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
            DaoAuthenticationProvider daoAuthenticationProvider,
            AuthenticationEventPublisher authenticationEventPublisher) throws Exception {
        AuthenticationManager manager = http.getSharedObject(AuthenticationManagerBuilder.class)
            .authenticationProvider(daoAuthenticationProvider)
            .build();
        // ProviderManager.build() no conecta un AuthenticationEventPublisher por
        // defecto en este armado manual — se lo setea explícito para que
        // LoginAttemptListener reciba los eventos de éxito/fallo.
        if (manager instanceof ProviderManager providerManager) {
            providerManager.setAuthenticationEventPublisher(authenticationEventPublisher);
        }
        return manager;
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }
}

