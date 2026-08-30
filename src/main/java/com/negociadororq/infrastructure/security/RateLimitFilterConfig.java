package com.negociadororq.infrastructure.security;

import java.io.IOException;
import java.time.LocalDateTime;

import com.factoring.commons.web.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Arma el {@link RateLimitFilter} compartido (Fase 11 del pipeline de
 * mantenibilidad, extraído a {@code ms-commons-util}) con el formato de
 * error 429 propio de este microservicio ({@link SecurityErrorResponse}).
 */
@Configuration
public class RateLimitFilterConfig {

    /**
     * Construye el filtro de rate-limiting con la configuración de este
     * microservicio.
     *
     * @param redisTemplate template de Redis para operaciones String
     * @param objectMapper  serializador usado para escribir la respuesta 429
     * @param enabled       si el límite está activo (permite desactivarlo por ambiente)
     * @param maxRequests   máximo de solicitudes permitidas por IP dentro de la ventana
     * @param windowSeconds duración de la ventana, en segundos
     * @return filtro de rate-limiting listo para registrar en la cadena de seguridad
     */
    @Bean
    public RateLimitFilter rateLimitFilter(final StringRedisTemplate redisTemplate, final ObjectMapper objectMapper,
            @Value("${rate-limit.ip.enabled:true}") final boolean enabled,
            @Value("${rate-limit.ip.max-requests:100}") final long maxRequests,
            @Value("${rate-limit.ip.window-seconds:60}") final long windowSeconds) {
        return new RateLimitFilter(redisTemplate, "negociador-orq", enabled, maxRequests, windowSeconds,
                (request, response) -> writeTooManyRequestsResponse(request, response, objectMapper));
    }

    private void writeTooManyRequestsResponse(final HttpServletRequest request, final HttpServletResponse response,
            final ObjectMapper objectMapper) throws IOException {
        final SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .codigo("RATE_LIMIT_EXCEDIDO")
                .mensaje("Demasiadas solicitudes desde tu direccion IP. Intenta de nuevo mas tarde.")
                .path(request.getRequestURI())
                .build();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
