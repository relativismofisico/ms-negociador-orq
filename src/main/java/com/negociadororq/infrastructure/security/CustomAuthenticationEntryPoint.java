package com.negociadororq.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

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

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("Acceso no autenticado a {}: {}", request.getRequestURI(), authException.getMessage());

        String codigo;
        String mensaje;

        String causeMsg = authException.getCause() != null ? authException.getCause().getMessage() : "";
        if (causeMsg.contains("expirado") || causeMsg.contains("expired") || causeMsg.contains("Expired")) {
            codigo = "TOKEN_EXPIRADO";
            mensaje = "El token JWT ha expirado. Por favor autentíquese nuevamente.";
        } else if (request.getHeader("Authorization") == null) {
            codigo = "TOKEN_AUSENTE";
            mensaje = "Se requiere un token JWT en el header Authorization: Bearer <token>";
        } else {
            codigo = "TOKEN_INVALIDO";
            mensaje = "El token JWT es inválido o está mal formado.";
        }

        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .codigo(codigo)
                .mensaje(mensaje)
                .path(request.getRequestURI())
                .build();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}

