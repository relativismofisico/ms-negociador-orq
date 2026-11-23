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

    /**
     * Fase 28d (A5b): descuenta en UNA sentencia, solo si alcanza (sin leer y escribir: dos operaciones concurrentes no
     * pierden una actualización).
     *
     * @param userId id del usuario
     * @param monto  monto a descontar
     * @return {@code true} si había cupo y se descontó
     */
    boolean descontar(Long userId, java.math.BigDecimal monto);

    /**
     * Fase 28d (A5b): devuelve en UNA sentencia.
     *
     * @param userId       id del usuario
     * @param monto        monto a devolver
     * @param sinNegativos si el resultado se limita a cero (como {@code revertirCupoUsuario})
     * @return {@code true} si el cupo existe
     */
    boolean devolver(Long userId, java.math.BigDecimal monto, boolean sinNegativos);
}
