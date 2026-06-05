package com.negociadororq.application.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 422 Unprocessable Entity.
 * Usar cuando la petición es sintácticamente válida pero viola una regla de negocio.
 * El recurso existe, los datos son parseable, pero la operación no puede completarse.
 *
 * Casos de uso:
 * - Factura no negociable (estado incorrecto)
 * - Cupo insuficiente (operador, empresa, fondeador)
 * - Factura vencida
 * - Actor no determinable para operación
 */
public class ReglaDeNegocioException extends NegociadorException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje, HttpStatus.UNPROCESSABLE_ENTITY, "REGLA_DE_NEGOCIO_VIOLADA");
    }

    public ReglaDeNegocioException(String mensaje, String codigo) {
        super(mensaje, HttpStatus.UNPROCESSABLE_ENTITY, codigo);
    }
}