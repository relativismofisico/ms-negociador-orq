package com.negociadororq.application.exception;

/**
 * Excepción base corporativa para todos los microservicios de la plataforma.
 * Toda excepción de dominio debe extender esta clase; el mapeo a un status
 * HTTP concreto es responsabilidad exclusiva de web/exception/GlobalExceptionHandler
 * (por tipo de excepción), no de esta jerarquía — application/ no debe conocer
 * el framework HTTP.
 *
 * Convención de códigos de error: SNAKE_UPPER_CASE, descriptivo del recurso/acción.
 * Ejemplos: FACTURA_NO_ENCONTRADA, CUPO_INSUFICIENTE, ENDOSO_DUPLICADO
 */
public abstract class NegociadorException extends RuntimeException {

    private final String codigo;

    protected NegociadorException(String mensaje, String codigo) {
        super(mensaje);
        this.codigo = codigo;
    }

    /**
     * @return el código de error de dominio en SNAKE_UPPER_CASE
     */
    public String getCodigo() {
        return codigo;
    }
}

