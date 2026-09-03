package com.negociadororq.application.exception;

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

    /**
     * Crea la excepción con el código de error genérico {@code RECURSO_NO_ENCONTRADO}.
     *
     * @param mensaje mensaje legible describiendo qué recurso no se encontró
     */
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje, "RECURSO_NO_ENCONTRADO");
    }

    /**
     * Crea la excepción con un código de error específico del caso de uso.
     *
     * @param mensaje mensaje legible describiendo qué recurso no se encontró
     * @param codigo  código de error de dominio en SNAKE_UPPER_CASE
     */
    public RecursoNoEncontradoException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}

