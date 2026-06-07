package com.negociadororq.application.exception.handler;

import com.negociadororq.application.exception.NegociadorException;
import com.negociadororq.application.exception.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.java.Log;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones HTTP para ms-negociador-orq.
 *
 * ESTÁNDAR CORPORATIVO — Estrategia de GlobalExceptionHandler reutilizable:
 *
 * 1. Copiar esta clase en cada microservicio nuevo.
 * 2. Solo cambiar el nombre del logger y el package.
 * 3. Agregar @ExceptionHandler específicos si el MS tiene excepciones adicionales.
 * 4. NUNCA lanzar RuntimeException sin wrapper en servicios que alcancen controllers.
 * 5. Toda excepción de dominio debe extender NegociadorException.
 *
 * Jerarquía de manejo (orden de precedencia):
 *   NegociadorException     → código HTTP definido en la excepción (4xx)
 *   MethodArgumentNotValid  → 400 (falló @Valid en el body)
 *   HttpMessageNotReadable  → 400 (body malformado / JSON inválido)
 *   MethodNotSupported      → 405 (verbo HTTP incorrecto)
 *   MissingRequestParam     → 400 (parámetro de query obligatorio ausente)
 *   MethodArgumentTypeMismatch → 400 (path variable con tipo incorrecto)
 *   Exception               → 500 (fallback — nunca exponer detalles internos)
 */
@Log
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // Excepciones de dominio (NegociadorException y subclases)
    // =========================================================================

    @ExceptionHandler(NegociadorException.class)
    public ResponseEntity<ErrorResponse> handleNegociadorException(
            NegociadorException ex, HttpServletRequest request) {

        log.warning(String.format("Excepcion de dominio [%s] en %s: %s", ex.getCodigo(), request.getRequestURI(), ex.getMessage()));

        return ResponseEntity
                .status(ex.getStatus())
                .body(buildError(ex.getStatus(), ex.getCodigo(), ex.getMessage(), request.getRequestURI(), null));
    }

    // =========================================================================
    // Excepciones de Spring MVC / validación
    // =========================================================================

    /**
     * Errores de validación de Bean Validation (@Valid, @NotNull, etc.)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        log.warning(String.format("Validacion fallida en %s: %s", request.getRequestURI(), detalles));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDACION_FALLIDA",
                        "Errores de validación en el cuerpo de la petición",
                        request.getRequestURI(),
                        detalles));
    }

    /**
     * Body JSON malformado o con tipo incorrecto
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warning(String.format("Cuerpo de peticion invalido en %s: %s", request.getRequestURI(), ex.getMessage()));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "CUERPO_INVALIDO",
                        "El cuerpo de la petición no es válido o está mal formado",
                        request.getRequestURI(),
                        null));
    }

    /**
     * Content-Type no soportado por el endpoint (ej: text/plain en vez de application/json)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warning(String.format("Content-Type no soportado en %s: %s", request.getRequestURI(), ex.getContentType()));

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(buildError(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "TIPO_CONTENIDO_NO_SOPORTADO",
                        "Content-Type '" + ex.getContentType() + "' no está soportado para este endpoint",
                        request.getRequestURI(),
                        null));
    }

    /**
     * Verbo HTTP no soportado por el endpoint (ej: GET en vez de POST)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warning(String.format("Metodo no soportado %s en %s", ex.getMethod(), request.getRequestURI()));

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(buildError(
                        HttpStatus.METHOD_NOT_ALLOWED,
                        "METODO_NO_PERMITIDO",
                        "Método HTTP '" + ex.getMethod() + "' no está soportado para este endpoint",
                        request.getRequestURI(),
                        null));
    }

    /**
     * Parámetro de query obligatorio ausente
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "PARAMETRO_REQUERIDO",
                        "El parámetro '" + ex.getParameterName() + "' es obligatorio",
                        request.getRequestURI(),
                        null));
    }

    /**
     * Path variable con tipo incompatible (ej: texto donde se espera número)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "PARAMETRO_INVALIDO",
                        "El parámetro '" + ex.getName() + "' tiene un valor con formato inválido",
                        request.getRequestURI(),
                        null));
    }

    // =========================================================================
    // Fallback — cualquier excepción no manejada
    // =========================================================================

    /**
     * Captura de último recurso. NUNCA exponer detalles de la excepción al cliente.
     * El stacktrace se loguea en ERROR para diagnóstico interno.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.log(java.util.logging.Level.SEVERE, String.format("Error inesperado en %s: %s", request.getRequestURI(), ex.getMessage()), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ERROR_INTERNO",
                        "Se produjo un error inesperado. Por favor contacte al administrador del sistema.",
                        request.getRequestURI(),
                        null));
    }

    // =========================================================================
    // Helper privado
    // =========================================================================

    private ErrorResponse buildError(HttpStatus status, String codigo, String mensaje,
                                     String path, List<String> detalles) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .codigo(codigo)
                .mensaje(mensaje)
                .path(path)
                .detalles(detalles)
                .build();
    }
}

