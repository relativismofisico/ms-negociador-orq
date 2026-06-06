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

    public ConflictoException(String mensaje) {
        super(mensaje, HttpStatus.CONFLICT, "CONFLICTO");
    }

    public ConflictoException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.CONFLICT, codigo);
    }
}

