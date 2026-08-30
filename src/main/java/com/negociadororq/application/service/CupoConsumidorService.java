package com.negociadororq.application.service;

import java.math.BigDecimal;

/**
 * Consulta y descuenta el cupo individual de negociación de un usuario
 * operador.
 */
public interface CupoConsumidorService {

    /**
     * @param userId id del usuario
     * @return el cupo disponible actual del usuario
     */
    BigDecimal obtenerCupoDisponible(Long userId);

    /**
     * @param userId id del usuario
     * @param monto  monto a descontar del cupo disponible
     */
    void descontarCupo(Long userId, BigDecimal monto);
}

