package com.negociadororq.infrastructure.security;

import java.util.Base64;

/**
 * Valida y decodifica, al arrancar, un secreto HMAC compartido que llega por variable de entorno (Base64 estándar, el
 * formato de {@code openssl rand -base64 48}). Exige entre 48 y 63 bytes decodificados: el mínimo de 48 (384 bits) es
 * el que exige HS384, el único algoritmo con el que la plataforma firma y verifica (fijado explícitamente en código,
 * ya no depende del largo de la clave); el máximo se conserva por prudencia. Los mensajes de error nunca incluyen el
 * valor.
 */
public final class SecretoHmac {

    /** Mínimo de bytes decodificados: 48 (384 bits), lo que exige HS384. */
    public static final int MIN_BYTES = 48;

    /** Máximo de bytes decodificados: se conserva por prudencia. */
    public static final int MAX_BYTES = 63;

    private SecretoHmac() {
    }

    /**
     * Decodifica el secreto o falla con un mensaje que explica cómo definirlo.
     *
     * @param propiedad nombre de la propiedad de Spring (solo para el mensaje)
     * @param variable  variable de entorno que la alimenta (solo para el mensaje)
     * @param valor     valor resuelto de la propiedad
     * @return los bytes de la clave
     * @throws IllegalStateException si falta, quedó sin resolver, no es Base64 o mide fuera de rango
     */
    public static byte[] decodificar(String propiedad, String variable, String valor) {
        String ayuda = " Defina la variable de entorno " + variable + " con un valor Base64 de " + MIN_BYTES
                + " a " + MAX_BYTES + " bytes (openssl rand -base64 48), igual en todos los servicios del ambiente.";
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta el secreto '" + propiedad + "'." + ayuda);
        }
        if (valor.startsWith("${")) {
            throw new IllegalStateException("El secreto '" + propiedad + "' quedó sin resolver." + ayuda);
        }
        byte[] clave;
        try {
            clave = Base64.getDecoder().decode(valor);
        } catch (IllegalArgumentException e) {
            // Sin encadenar la causa: su mensaje cita el carácter inválido del secreto.
            throw new IllegalStateException("El secreto '" + propiedad + "' no es Base64 válido." + ayuda);
        }
        if (clave.length < MIN_BYTES || clave.length > MAX_BYTES) {
            throw new IllegalStateException("El secreto '" + propiedad + "' mide " + clave.length + " bytes." + ayuda);
        }
        return clave;
    }
}
