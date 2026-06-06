package com.negociadororq.application.service;

import java.math.BigDecimal;

public interface CupoConsumidorService {
    BigDecimal obtenerCupoDisponible(Long userId);
    void descontarCupo(Long userId, BigDecimal monto);
}

