package com.negociadororq.application.service;

/**
 * Bloqueo temporal de cuenta por intentos fallidos de autenticación
 * (Basic Auth de servicio u operario de BD), respaldado por Redis
 * (compartido entre todas las instancias del servicio).
 */
public interface LoginLockoutService {

    /**
     * Indica si la cuenta está bloqueada por haber superado el máximo de
     * intentos fallidos configurado dentro de la ventana vigente.
     *
     * @param username usuario a chequear
     * @return {@code true} si está bloqueada
     */
    boolean isLocked(String username);

    /**
     * Registra un intento fallido de autenticación para el usuario. El
     * contador expira solo (autoexpira) al vencer la ventana configurada.
     *
     * @param username usuario que falló la autenticación
     */
    void registerFailure(String username);

    /**
     * Limpia el contador de intentos fallidos, típicamente tras una
     * autenticación exitosa.
     *
     * @param username usuario a resetear
     */
    void reset(String username);
}
