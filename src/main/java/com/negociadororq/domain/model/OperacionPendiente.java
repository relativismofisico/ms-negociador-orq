package com.negociadororq.domain.model;

import java.time.LocalDateTime;

/**
 * Fase 28d, opción A: una operación mixta (MySQL + Mongo) en curso o pendiente de completar.
 *
 * @param id                 id de la fila
 * @param tipo               qué operación es
 * @param claveNatural       clave natural, con el CUFE ({@link #clave})
 * @param estado             estado del flujo de ese tipo (lo define cada flujo)
 * @param datos              datos mínimos para reanudarla (JSON del flujo), o null
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
