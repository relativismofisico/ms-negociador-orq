package com.negociadororq.application.exception.handler;

import com.negociadororq.application.exception.AccionNoPermitidaException;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.NegociadorException;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.exception.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler — mapeo de excepciones a respuestas HTTP")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/test/endpoint");
    }

    // ─── NegociadorException ──────────────────────────────────────

    @Test
    @DisplayName("RecursoNoEncontradoException → 404 NOT_FOUND")
    void recursoNoEncontrado_retorna404() {
        RecursoNoEncontradoException ex = new RecursoNoEncontradoException("No encontrado", "REC_NOT_FOUND");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCodigo()).isEqualTo("REC_NOT_FOUND");
        assertThat(response.getBody().getMensaje()).isEqualTo("No encontrado");
        assertThat(response.getBody().getPath()).isEqualTo("/test/endpoint");
    }

    @Test
    @DisplayName("PeticionInvalidaException → 400 BAD_REQUEST")
    void peticionInvalida_retorna400() {
        PeticionInvalidaException ex = new PeticionInvalidaException("Campo requerido", "CAMPO_REQUERIDO");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCodigo()).isEqualTo("CAMPO_REQUERIDO");
    }

    @Test
    @DisplayName("ConflictoException → 409 CONFLICT")
    void conflicto_retorna409() {
        ConflictoException ex = new ConflictoException("Duplicado", "DUPLICADO");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("AccionNoPermitidaException → 403 FORBIDDEN")
    void accionNoPermitida_retorna403() {
        AccionNoPermitidaException ex = new AccionNoPermitidaException("Sin permiso", "SIN_PERMISO");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ReglaDeNegocioException → 422 UNPROCESSABLE_ENTITY")
    void reglaDeNegocio_retorna422() {
        ReglaDeNegocioException ex = new ReglaDeNegocioException("Regla violada", "REGLA_VIOLADA");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("NegociadorException sin código → body con codigo null")
    void negociadorExceptionSinCodigo_codigoEnBody() {
        RecursoNoEncontradoException ex = new RecursoNoEncontradoException("Solo mensaje");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getBody().getMensaje()).isEqualTo("Solo mensaje");
    }

    @Test
    @DisplayName("ErrorResponse tiene timestamp no nulo")
    void errorResponse_conTimestamp() {
        PeticionInvalidaException ex = new PeticionInvalidaException("error");

        ResponseEntity<ErrorResponse> response = handler.handleNegociadorException(ex, request);

        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    // ─── MethodArgumentNotValidException ─────────────────────────

    @Test
    @DisplayName("handleValidationException → 400 con lista de detalles de campos")
    void validationException_retorna400ConDetalles() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "campo", "no puede ser nulo");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCodigo()).isEqualTo("VALIDACION_FALLIDA");
        assertThat(response.getBody().getDetalles()).hasSize(1);
        assertThat(response.getBody().getDetalles().get(0)).contains("campo");
        assertThat(response.getBody().getDetalles().get(0)).contains("no puede ser nulo");
    }

    @Test
    @DisplayName("handleValidationException con múltiples errores incluye todos")
    void validationException_multiplesErrores() {
        BindingResult bindingResult = mock(BindingResult.class);
        List<FieldError> errors = List.of(
                new FieldError("obj", "campo1", "error1"),
                new FieldError("obj", "campo2", "error2")
        );
        when(bindingResult.getFieldErrors()).thenReturn(errors);

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

        assertThat(response.getBody().getDetalles()).hasSize(2);
    }

    // ─── HttpMessageNotReadableException ─────────────────────────

    @Test
    @DisplayName("handleMessageNotReadable → 400 con código CUERPO_INVALIDO")
    void messageNotReadable_retorna400() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCodigo()).isEqualTo("CUERPO_INVALIDO");
    }

    // ─── HttpRequestMethodNotSupportedException ───────────────────

    @Test
    @DisplayName("handleMethodNotSupported → 405 con código METODO_NO_PERMITIDO")
    void methodNotSupported_retorna405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getCodigo()).isEqualTo("METODO_NO_PERMITIDO");
        assertThat(response.getBody().getMensaje()).contains("DELETE");
    }

    // ─── MissingServletRequestParameterException ─────────────────

    @Test
    @DisplayName("handleMissingParameter → 400 con nombre del parámetro faltante")
    void missingParameter_retorna400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("idNegociacion", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCodigo()).isEqualTo("PARAMETRO_REQUERIDO");
        assertThat(response.getBody().getMensaje()).contains("idNegociacion");
    }

    // ─── MethodArgumentTypeMismatchException ─────────────────────

    @Test
    @DisplayName("handleTypeMismatch → 400 con nombre del parámetro con tipo inválido")
    void typeMismatch_retorna400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("idNegociacion");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCodigo()).isEqualTo("PARAMETRO_INVALIDO");
        assertThat(response.getBody().getMensaje()).contains("idNegociacion");
    }

    // ─── Fallback genérico ────────────────────────────────────────

    @Test
    @DisplayName("handleGenericException → 500 con código ERROR_INTERNO")
    void genericException_retorna500() {
        Exception ex = new RuntimeException("Error inesperado de BD");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCodigo()).isEqualTo("ERROR_INTERNO");
        assertThat(response.getBody().getMensaje()).doesNotContain("Error inesperado de BD");
    }

    @Test
    @DisplayName("handleGenericException incluye el path en la respuesta")
    void genericException_incluyePath() {
        when(request.getRequestURI()).thenReturn("/api/v1/negociar/factura");
        Exception ex = new RuntimeException("fallo");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/negociar/factura");
    }

    @Test
    @DisplayName("handleGenericException tiene detalles null (no expone internos)")
    void genericException_detallesNull() {
        Exception ex = new RuntimeException("datos sensibles internos");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getBody().getDetalles()).isNull();
    }
}