package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 409 Conflict.
 * Usar cuando la petición no puede completarse debido a un conflicto con
 * el estado actual del recurso en el servidor.
 *
 * Casos de uso:
 * - Intento de crear un endoso duplicado (mismo actor, misma factura)
 * - Recurso que ya existe y no permite duplicados
 */
public class ConflictoException extends NegociadorException {

    /**
     * Crea la excepción con el código de error genérico {@code CONFLICTO}.
     *
     * @param mensaje mensaje legible describiendo el conflicto
     */
    public ConflictoException(String mensaje) {
        super(mensaje, HttpStatus.CONFLICT, "CONFLICTO");
    }

    /**
     * Crea la excepción con un código de error específico del caso de uso.
     *
     * @param mensaje mensaje legible describiendo el conflicto
     * @param codigo  código de error de dominio en SNAKE_UPPER_CASE
     */
    public ConflictoException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.CONFLICT, codigo);
    }
}

