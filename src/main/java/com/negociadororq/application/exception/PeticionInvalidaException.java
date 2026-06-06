package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

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

    public PeticionInvalidaException(String mensaje) {
        super(mensaje, HttpStatus.BAD_REQUEST, "PETICION_INVALIDA");
    }

    public PeticionInvalidaException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.BAD_REQUEST, codigo);
    }
}
