package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción base corporativa para todos los microservicios de la plataforma.
 * Toda excepción de dominio debe extender esta clase para garantizar un
 * mapeo HTTP consistente a través del GlobalExceptionHandler.
 *
 * Convención de códigos de error: SNAKE_UPPER_CASE, descriptivo del recurso/acción.
 * Ejemplos: FACTURA_NO_ENCONTRADA, CUPO_INSUFICIENTE, ENDOSO_DUPLICADO
 */
public abstract class NegociadorException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    protected NegociadorException(String mensaje, HttpStatus status, String codigo) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
