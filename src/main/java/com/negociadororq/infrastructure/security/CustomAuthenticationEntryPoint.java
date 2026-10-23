package com.negociadororq.infrastructure.security;

import java.io.IOException;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Manejador de errores 401 Unauthorized.
 *
 * Se activa cuando:
 * - El header Authorization está ausente.
 * - El JWT tiene firma inválida.
 * - El JWT ha expirado.
 * - El JWT está mal formado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Mensaje único de cualquier 401: el motivo concreto solo va al log. */
    static final String MENSAJE_NO_AUTENTICADO = "Token inválido o expirado.";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("Acceso no autenticado a {}: {}", request.getRequestURI(), authException.getMessage());

        // El código distingue la categoría (contrato de la API); el mensaje es único y el motivo concreto
        // (texto de jjwt o de Spring Security) solo va al log (ms-security-util#55).
        String codigo;
        if (esTokenExpirado(authException)) {
            codigo = "TOKEN_EXPIRADO";
        } else if (request.getHeader("Authorization") == null) {
            codigo = "TOKEN_AUSENTE";
        } else {
            codigo = "TOKEN_INVALIDO";
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .codigo(codigo)
                .mensaje(MENSAJE_NO_AUTENTICADO)
                .path(request.getRequestURI())
                .build();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    /**
     * Por el tipo de la causa, no por su texto: el decoder envuelve la {@link ExpiredJwtException} de jjwt.
     */
    private static boolean esTokenExpirado(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ExpiredJwtException) {
                return true;
            }
        }
        return false;
    }
}
