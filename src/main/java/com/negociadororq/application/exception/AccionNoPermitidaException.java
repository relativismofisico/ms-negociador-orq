package com.negociadororq.application.exception;

/**
 * HTTP 403 Forbidden.
 * Usar cuando el solicitante no tiene permisos para ejecutar la operación,
 * aunque esté autenticado. La autorización de negocio falló.
 *
 * Casos de uso:
 * - Empresa que no es proveedor intenta hacer FACTORING
 * - Empresa que no es pagador intenta hacer CONFIRMING
 * - Actor que no tiene rol en la factura intenta operar sobre ella
 */
public class AccionNoPermitidaException extends NegociadorException {

    /**
     * Crea la excepción con el código de error genérico {@code ACCION_NO_PERMITIDA}.
     *
     * @param mensaje mensaje legible describiendo la acción no permitida
     */
    public AccionNoPermitidaException(String mensaje) {
        super(mensaje, "ACCION_NO_PERMITIDA");
    }

    /**
     * Crea la excepción con un código de error específico del caso de uso.
     *
     * @param mensaje mensaje legible describiendo la acción no permitida
     * @param codigo  código de error de dominio en SNAKE_UPPER_CASE
     */
    public AccionNoPermitidaException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}

