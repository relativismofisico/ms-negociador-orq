package com.negociadororq.application.util;

import java.time.LocalDate;

/**
 * Valida si una fecha está dentro de la ventana de tiempo permitida para
 * descontar/endosar una factura, según {@code vigenciaDescuento}.
 *
 * <p>No es parte del cálculo de liquidación (eso vive en ms-liquidador-neg):
 * es una compuerta de elegibilidad temprana, en negociación y endoso.</p>
 *
 * <ul>
 *   <li>{@code TODO_EL_PLAZO}: sin restricción.</li>
 *   <li>{@code PRIMEROS_DIAS}: solo hasta {@code diasVigencia} días después de
 *       la fecha de negociación. Si la factura todavía no fue negociada
 *       (fechaNegociacion nula, caso del chequeo al momento de negociar), la
 *       ventana recién arranca hoy — trivialmente dentro.</li>
 *   <li>{@code DIAS_ANTES_VENCIMIENTO}: solo hasta {@code diasVigencia} días
 *       antes del vencimiento de la factura.</li>
 * </ul>
 */
public final class VigenciaDescuentoValidator {

    private static final String TODO_EL_PLAZO = "TODO_EL_PLAZO";
    private static final String PRIMEROS_DIAS = "PRIMEROS_DIAS";
    private static final String DIAS_ANTES_VENCIMIENTO = "DIAS_ANTES_VENCIMIENTO";

    private VigenciaDescuentoValidator() {
    }

    /**
     * @param hoy               fecha contra la que se evalúa la vigencia
     * @param fechaNegociacion  fecha en que se negoció la factura, o {@code null} si aún no se negoció
     * @param fechaVencimiento  fecha de vencimiento de la factura
     * @param vigenciaDescuento TODO_EL_PLAZO, PRIMEROS_DIAS o DIAS_ANTES_VENCIMIENTO
     * @param diasVigencia      días de la ventana, según {@code vigenciaDescuento}
     * @return true si {@code hoy} todavía está dentro de la ventana permitida
     */
    public static boolean dentroDeVigencia(
            LocalDate hoy,
            LocalDate fechaNegociacion,
            LocalDate fechaVencimiento,
            String vigenciaDescuento,
            Integer diasVigencia) {

        if (vigenciaDescuento == null || TODO_EL_PLAZO.equals(vigenciaDescuento) || diasVigencia == null) {
            return true;
        }

        if (PRIMEROS_DIAS.equals(vigenciaDescuento)) {
            return fechaNegociacion == null || !hoy.isAfter(fechaNegociacion.plusDays(diasVigencia));
        }

        if (DIAS_ANTES_VENCIMIENTO.equals(vigenciaDescuento)) {
            return fechaVencimiento == null || !hoy.isAfter(fechaVencimiento.minusDays(diasVigencia));
        }

        return true;
    }
}
