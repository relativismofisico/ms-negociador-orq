package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.User;

/**
 * Puerto de solo lectura para datos de usuarios, replicados desde
 * ms-security-util.
 */
public interface UserOutputPort {

    /**
     * @param username nombre de usuario
     * @return el usuario, si existe
     */
    Optional<User> findByUsername(String username);
}
