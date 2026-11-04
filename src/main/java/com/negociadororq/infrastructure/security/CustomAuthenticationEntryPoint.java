package com.negociadororq.infrastructure.security;

import java.io.IOException;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

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
    static final String CODIGO_NO_DISPONIBLE = "SERVICIO_NO_DISPONIBLE";
    static final String MENSAJE_NO_DISPONIBLE =
            "Servicio no disponible temporalmente: no se pudo validar la sesión. Intente de nuevo en unos momentos.";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        if (esFalloDeInfraestructura(authException)) {
            // Basic Auth servicio-a-servicio con la base caída: DaoAuthenticationProvider envuelve el error de datos
            // en InternalAuthenticationServiceException. No son credenciales inválidas: 503, no 401.
            log.warn("No se pudo validar la autenticación en {}: la base de datos no respondió",
                    request.getRequestURI());
            responderNoDisponible(request, response);
            return;
        }

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

        escribir(request, response, HttpStatus.UNAUTHORIZED, codigo, MENSAJE_NO_AUTENTICADO);
    }

    /**
     * 503 con mensaje fijo cuando la base no respondió al validar la autenticación (sin detalle interno).
     *
     * @param request  petición en curso
     * @param response respuesta a escribir
     * @throws IOException si falla la escritura de la respuesta
     */
    public void responderNoDisponible(HttpServletRequest request, HttpServletResponse response) throws IOException {
        escribir(request, response, HttpStatus.SERVICE_UNAVAILABLE, CODIGO_NO_DISPONIBLE, MENSAJE_NO_DISPONIBLE);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String codigo, String mensaje) throws IOException {
        SecurityErrorResponse errorResponse = SecurityErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .codigo(codigo)
                .mensaje(mensaje)
                .path(request.getRequestURI())
                .build();

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status.value());
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private static boolean esFalloDeInfraestructura(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof DataAccessException || causa instanceof TransactionException
                    || causa instanceof ValidacionTokenNoDisponibleException) {
                return true;
            }
        }
        return false;
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
