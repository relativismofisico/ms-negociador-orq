package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.FondeadorQuota;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class QuotaServiceImpl implements QuotaService {

    @Override
    public void validarQuotaOperacion(String tipoOperacion, FondeadorQuota quota,
            BigDecimal valorFactura, String numeroFactura, String rutFondeador) {

        if (tipoOperacion == null) {
            throw new RuntimeException("Tipo operación inválido");
        }

        switch (tipoOperacion.toUpperCase()) {
            case "FACTORING" -> validarSupplierQuota(quota, valorFactura, numeroFactura, rutFondeador);
            case "CONFIRMING" -> validarPayerQuota(quota, valorFactura, numeroFactura, rutFondeador);
            default -> throw new RuntimeException("Tipo operación no soportado: " + tipoOperacion);
        }
    }

    @Override
    public void descontarQuotaOperacion(String tipoOperacion, FondeadorQuota quota, BigDecimal valorFactura) {

        if (tipoOperacion == null) {
            throw new RuntimeException("Tipo operación inválido");
        }

        switch (tipoOperacion.toUpperCase()) {
            case "FACTORING" -> {
                BigDecimal actual = toBigDecimal(quota.getSupplierCurrentQuota());
                quota.setSupplierCurrentQuota(actual.subtract(valorFactura).max(BigDecimal.ZERO));
            }

            case "CONFIRMING" -> {
                BigDecimal actual = toBigDecimal(quota.getPayerCurrentQuota());
                quota.setPayerCurrentQuota(actual.subtract(valorFactura).max(BigDecimal.ZERO));
            }

            default -> throw new RuntimeException("Tipo operación no soportado: " + tipoOperacion);
        }
    }

    private void validarSupplierQuota(FondeadorQuota quota, BigDecimal valorFactura, String numeroFactura, String rutFondeador) {

        BigDecimal disponible = toBigDecimal(quota.getSupplierCurrentQuota());

        if (disponible.compareTo(valorFactura) < 0) {
            throw new RuntimeException(
                    "Fondeador " + rutFondeador + " no tiene supplier_current_quota suficiente " + "para factura "
                            + numeroFactura + ". Disponible: " + disponible + ", requerido: " + valorFactura);
        }
    }

    private void validarPayerQuota(FondeadorQuota quota, BigDecimal valorFactura, String numeroFactura, String rutFondeador) {

        BigDecimal disponible = toBigDecimal(quota.getPayerCurrentQuota());

        if (disponible.compareTo(valorFactura) < 0) {

            throw new RuntimeException(
                    "Fondeador " + rutFondeador + " no tiene payer_current_quota suficiente " +
                            "para factura " + numeroFactura + ". Disponible: " + disponible + ", requerido: " + valorFactura);
        }
    }

    private BigDecimal toBigDecimal(Number value) {

        if (value == null) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value.toString());
    }
}
