package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

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

    public AccionNoPermitidaException(String mensaje) {
        super(mensaje, HttpStatus.FORBIDDEN, "ACCION_NO_PERMITIDA");
    }

    public AccionNoPermitidaException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.FORBIDDEN, codigo);
    }
}