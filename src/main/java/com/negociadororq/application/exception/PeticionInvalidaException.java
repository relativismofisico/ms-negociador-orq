package com.negociadororq.application.exception;

/**
 * HTTP 400 Bad Request.
 * Usar cuando la petición del cliente tiene datos faltantes, mal formados
 * o incompatibles con los requisitos del endpoint.
 *
 * Casos de uso:
 * - Lista de facturas vacía o nula en request de endoso
 * - Tipo de operación no informado
 * - Tipo de operación no soportado por el sistema
 */
public class PeticionInvalidaException extends NegociadorException {

    /**
     * Crea la excepción con el código de error genérico {@code PETICION_INVALIDA}.
     *
     * @param mensaje mensaje legible describiendo qué dato de la petición es inválido
     */
    public PeticionInvalidaException(String mensaje) {
        super(mensaje, "PETICION_INVALIDA");
    }

    /**
     * Crea la excepción con un código de error específico del caso de uso.
     *
     * @param mensaje mensaje legible describiendo qué dato de la petición es inválido
     * @param codigo  código de error de dominio en SNAKE_UPPER_CASE
     */
    public PeticionInvalidaException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}

