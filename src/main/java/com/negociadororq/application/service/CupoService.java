package com.negociadororq.application.service;

import java.math.BigDecimal;

public interface CupoService {
    void descontarCupoEmpresa(Long companyId, BigDecimal montoNegociado);
    public void descontarCupoFondeador(String fondeadorId, String proveedorId, String pagadorId, BigDecimal montoNegociado);
}
