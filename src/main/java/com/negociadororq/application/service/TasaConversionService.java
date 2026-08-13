package com.negociadororq.application.service;

import com.negociadororq.application.dto.TasaConversionResponseDto;
import java.math.BigDecimal;

public interface TasaConversionService {

    /**
     * Convierte una tasa expresada en un tipo (Tasa Efectiva Anual, Tasa Mes Vencido o Tasa Mes
     * Anticipado) a sus equivalentes en los otros dos tipos.
     *
     * @param valor    valor de la tasa, expresado como porcentaje (p. ej. 2.1 para 2.1%)
     * @param tipoTasa tipo de tasa en el que está expresado {@code valor}: TASA_EFECTIVA_ANUAL,
     *                 TASA_MES_VENCIDO o TASA_MES_ANTICIPADO
     */
    TasaConversionResponseDto convertir(BigDecimal valor, String tipoTasa);
}
