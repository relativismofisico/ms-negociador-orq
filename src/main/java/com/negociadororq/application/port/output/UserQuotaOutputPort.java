package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.UserQuota;

/**
 * Puerto de persistencia del cupo individual de negociación asignado a un usuario.
 */
public interface UserQuotaOutputPort {

    /**
     * @param userId id del usuario
     * @return el cupo del usuario, si existe
     */
    Optional<UserQuota> findByUserId(Long userId);

    /**
     * @param quota cupo a persistir
     * @return el cupo persistido
     */
    UserQuota save(UserQuota quota);
}
