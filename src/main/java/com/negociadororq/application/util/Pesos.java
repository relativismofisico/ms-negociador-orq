package com.negociadororq.application.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Fase 28d: montos en pesos colombianos para los textos al cliente ({@code 12000.5} → {@code "$12.000,50"}). Formatea
 * el {@link BigDecimal} exacto (sin pasar por double).
 */
public final class Pesos {

    private Pesos() {
    }

    /**
     * @param valor monto
     * @return el texto, o {@code "-"} si es null
     */
    public static String formato(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        formato.setParseBigDecimal(true);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return "$" + formato.format(valor);
    }
}
