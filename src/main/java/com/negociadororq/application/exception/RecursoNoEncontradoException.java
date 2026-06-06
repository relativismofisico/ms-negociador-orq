package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 404 Not Found.
 * Usar cuando una entidad buscada no existe en el sistema.
 *
 * Casos de uso:
 * - Factura no encontrada por número
 * - Negociación no encontrada por ID
 * - Operador/empresa/fondeador no encontrado por RUT/NIT
 */
public class RecursoNoEncontradoException extends NegociadorException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje, HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO");
    }

    public RecursoNoEncontradoException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.NOT_FOUND, codigo);
    }
}
