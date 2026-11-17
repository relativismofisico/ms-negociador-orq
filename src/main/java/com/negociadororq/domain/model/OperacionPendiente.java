package com.negociadororq.domain.model;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Fase 28d, opción A: una operación mixta (MySQL + Mongo) en curso o pendiente de completar.
 *
 * @param id                 id de la fila
 * @param tipo               qué operación es
 * @param claveNatural       clave natural, con el CUFE ({@link #clave})
 * @param estado             estado del flujo de ese tipo (lo define cada flujo)
 * @param datos              datos mínimos para reanudarla (JSON del flujo: ids, CUFE, RUTs, montos), o null. Nunca
 *                           información sensible: ni el OTP ni un hash suyo, ni tokens, claves o secretos
 *                           ({@link #exigirDatosNoSensibles})
 * @param leaseHasta         concesión del reconciliador que la tomó, o null
 * @param intentos           veces que el reconciliador la tomó
 * @param fechaCreacion      alta
 * @param fechaActualizacion última transición
 * @param ultimoError        último error al reanudarla, o null
 */
public record OperacionPendiente(Long id, TipoOperacionPendiente tipo, String claveNatural, String estado,
        String datos, LocalDateTime leaseHasta, int intentos, LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion, String ultimoError) {

    /** Separador de las partes de la clave natural. */
    public static final String SEPARADOR = "|";

    /** Campos que {@code datos} no puede llevar (en cualquier nivel del JSON). */
    private static final Pattern CAMPO_SENSIBLE = Pattern.compile(
            "\"[^\"]*(otp|codigo|hash|token|clave|password|secret)[^\"]*\"\\s*:", Pattern.CASE_INSENSITIVE);

    /**
     * Rechaza {@code datos} con un campo sensible (OTP, código, hash, token, clave, password, secreto): esa
     * información nunca se guarda en {@code operacion_pendiente}.
     *
     * @param datos JSON del flujo, o null
     * @return los mismos datos
     */
    public static String exigirDatosNoSensibles(final String datos) {
        if (datos != null && CAMPO_SENSIBLE.matcher(datos).find()) {
            throw new IllegalArgumentException("operacion_pendiente.datos no puede llevar información sensible");
        }
        return datos;
    }

    /**
     * Clave natural de una operación sobre una factura de una negociación: {@code negociacion|cufe}, nunca el número.
     *
     * @param negociacionId negociación
     * @param cufe          CUFE de la factura
     * @return la clave
     * @throws IllegalArgumentException si falta alguna parte o alguna lleva el separador
     */
    public static String clave(final String negociacionId, final String cufe) {
        return negociacionId(negociacionId) + SEPARADOR + parte(cufe, "cufe");
    }

    /**
     * Clave natural de una operación sobre la negociación entera (crear negociación).
     *
     * @param negociacionId negociación
     * @return la clave
     */
    public static String clave(final String negociacionId) {
        return negociacionId(negociacionId);
    }

    private static String negociacionId(final String negociacionId) {
        return parte(negociacionId, "negociacionId");
    }

    private static String parte(final String valor, final String nombre) {
        if (valor == null || valor.isBlank() || valor.contains(SEPARADOR)) {
            throw new IllegalArgumentException("Clave de operación sin " + nombre + " válido");
        }
        return valor;
    }
}
