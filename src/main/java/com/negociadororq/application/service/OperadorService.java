package com.negociadororq.application.service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface OperadorService {

    BigDecimal obtenerCupoDisponible(Long userId);

    void descontarCupo(Long userId, BigDecimal monto);

    void revertirCupo(Long userId, BigDecimal valor);

    void revertirCupoUsuario(Long userId, BigDecimal valor);

    void revertirCupoEmpresa(Long companyId, BigDecimal valor);

    void revertirCupoFondeador(Long funderId, Long payerId, BigDecimal valor);

}
