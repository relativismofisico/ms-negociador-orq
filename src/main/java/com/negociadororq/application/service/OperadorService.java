package com.negociadororq.application.service;

import java.math.BigDecimal;

public interface OperadorService {

    BigDecimal obtenerCupoDisponible(Long userId);

    void descontarCupo(Long userId, BigDecimal monto);

}
