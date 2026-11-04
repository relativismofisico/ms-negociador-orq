package com.negociadororq.infrastructure.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce {@link ValidacionTokenNoDisponibleException} (lanzada por {@link JjwtDecoder}) a 503 con un mensaje fijo.
 * Va delante del {@code BearerTokenAuthenticationFilter}: sin él, la excepción salía de la cadena, se reenviaba a
 * /error y el cliente recibía 401, y el front cerraba la sesión. No es un {@code @Component}: solo vive en la cadena
 * de seguridad.
 */
@Slf4j
public class ValidacionTokenNoDisponibleFilter extends OncePerRequestFilter {

    private final CustomAuthenticationEntryPoint entryPoint;

    /**
     * @param entryPoint escribe la respuesta 503 con el mismo formato que las de 401
     */
    public ValidacionTokenNoDisponibleFilter(CustomAuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ValidacionTokenNoDisponibleException e) {
            log.warn("No se pudo validar el usuario del token en {}: la base de datos no respondió",
                    request.getRequestURI());
            if (response.isCommitted()) {
                throw e;
            }
            response.resetBuffer();
            entryPoint.responderNoDisponible(request, response);
        }
    }
}
