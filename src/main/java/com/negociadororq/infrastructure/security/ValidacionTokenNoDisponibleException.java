package com.negociadororq.infrastructure.security;

/**
 * La base no respondió al comprobar el usuario del token (caída, timeout, sin conexión): no es un token inválido.
 *
 * <p>No es una {@code AuthenticationException} a propósito: así no la convierte en 401 el resource server. La
 * traduce a 503 {@link ValidacionTokenNoDisponibleFilter}.</p>
 */
public class ValidacionTokenNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param causa fallo de acceso a datos o de transacción al consultar el usuario
     */
    public ValidacionTokenNoDisponibleException(Throwable causa) {
        super("No se pudo validar el usuario del token: la base de datos no respondió.", causa);
    }
}
