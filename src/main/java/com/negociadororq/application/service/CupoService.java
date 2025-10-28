package com.negociadororq.application.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;

public interface CupoService {
    @Transactional
    void descontarCupoEmpresa(Long companyId, BigDecimal montoNegociado);
    @Transactional
    public void descontarCupoFondeador(String fondeadorId, String proveedorId, String pagadorId, BigDecimal montoNegociado);
}
