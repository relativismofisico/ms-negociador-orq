package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.FondeadorQuota;

import java.math.BigDecimal;

public interface QuotaService {
    void validarQuotaOperacion(String tipoOperacion, FondeadorQuota quota,
                               BigDecimal valorFactura, String numeroFactura, String rutFondeador);
    void descontarQuotaOperacion(String tipoOperacion, FondeadorQuota quota, BigDecimal valorFactura);
}