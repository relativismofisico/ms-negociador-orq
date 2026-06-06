package com.negociadororq.application.service;

import java.math.BigDecimal;

public interface CupoReversionService {
    void revertirCupo(Long userId, BigDecimal valor);
    void revertirCupoUsuario(Long userId, BigDecimal valor);
    void revertirCupoEmpresa(Long companyId, BigDecimal valor);
    void revertirCupoFondeadorConfirming(Long funderId, Long payerId, BigDecimal valor);
    void revertirCupoFondeadorFactoring(Long funderId, Long supplierId, BigDecimal valor);
}
