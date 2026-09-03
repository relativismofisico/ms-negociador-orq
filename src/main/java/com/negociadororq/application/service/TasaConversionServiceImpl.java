package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import com.negociadororq.application.dto.TasaConversionResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;

/**
 * Convierte una tasa entre Tasa Efectiva Anual (EA), Tasa Mes Vencido (interés compuesto pagado
 * al final del período) y Tasa Mes Anticipado (tasa de descuento pagada al inicio del período).
 *
 * <p>Toda conversión pasa primero por la Tasa Mes Vencido como pivote: se convierte el valor
 * recibido a su equivalente mensual vencido, y desde ahí se derivan los otros dos. Esto evita
 * tener que mantener las 6 fórmulas de conversión par a par por separado.</p>
 *
 * <p>Fórmulas (i expresado como fracción, no como porcentaje):</p>
 * <ul>
 *   <li>Efectiva Anual → Mes Vencido: i_mv = (1 + i_ea)^(1/12) - 1</li>
 *   <li>Mes Anticipado → Mes Vencido: i_mv = i_ma / (1 - i_ma)</li>
 *   <li>Mes Vencido → Efectiva Anual: i_ea = (1 + i_mv)^12 - 1</li>
 *   <li>Mes Vencido → Mes Anticipado: i_ma = i_mv / (1 + i_mv)</li>
 * </ul>
 */
public class TasaConversionServiceImpl implements TasaConversionService {

    private static final Set<String> TIPOS_VALIDOS =
        Set.of("TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO");

    private static final double MESES_POR_ANIO = 12.0;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final int ESCALA_RESULTADO = 4;

    @Override
    public TasaConversionResponseDto convertir(BigDecimal valor, String tipoTasa) {
        validar(valor, tipoTasa);

        double fraccionOriginal = valor.doubleValue() / CIEN.doubleValue();
        double fraccionMesVencido = aMesVencido(fraccionOriginal, tipoTasa);

        double fraccionEfectivaAnual = Math.pow(1.0 + fraccionMesVencido, MESES_POR_ANIO) - 1.0;
        double fraccionMesAnticipado = fraccionMesVencido / (1.0 + fraccionMesVencido);

        return TasaConversionResponseDto.builder()
                .valorOriginal(valor)
                .tipoTasaOriginal(tipoTasa)
                .tasaEfectivaAnual(aPorcentaje(fraccionEfectivaAnual))
                .tasaMesVencido(aPorcentaje(fraccionMesVencido))
                .tasaMesAnticipado(aPorcentaje(fraccionMesAnticipado))
                .build();
    }

    private double aMesVencido(double fraccion, String tipoTasa) {
        return switch (tipoTasa) {
            case "TASA_MES_VENCIDO" -> fraccion;
            case "TASA_EFECTIVA_ANUAL" -> Math.pow(1.0 + fraccion, 1.0 / MESES_POR_ANIO) - 1.0;
            case "TASA_MES_ANTICIPADO" -> fraccion / (1.0 - fraccion);
            default -> throw new PeticionInvalidaException("Tipo de tasa no soportado: " + tipoTasa);
        };
    }

    private BigDecimal aPorcentaje(double fraccion) {
        return BigDecimal.valueOf(fraccion * CIEN.doubleValue()).setScale(ESCALA_RESULTADO, RoundingMode.HALF_UP);
    }

    private void validar(BigDecimal valor, String tipoTasa) {
        if (valor == null) {
            throw new PeticionInvalidaException("El valor de la tasa es obligatorio");
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new PeticionInvalidaException("El valor de la tasa no puede ser negativo");
        }
        if (tipoTasa == null || tipoTasa.isBlank()) {
            throw new PeticionInvalidaException("El tipo de tasa es obligatorio");
        }
        if (!TIPOS_VALIDOS.contains(tipoTasa)) {
            throw new PeticionInvalidaException("Tipo de tasa no soportado: " + tipoTasa
                + ". Valores permitidos: " + TIPOS_VALIDOS);
        }
        if ("TASA_MES_ANTICIPADO".equals(tipoTasa) && valor.compareTo(CIEN) >= 0) {
            throw new PeticionInvalidaException("Una tasa mes anticipado debe ser menor a 100%");
        }
    }
}
