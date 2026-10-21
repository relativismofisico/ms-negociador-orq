package com.negociadororq.domain.util;

/**
 * Enmascara valores sensibles (contraseñas, secretos, tokens, códigos OTP) en los {@code toString()} de DTOs, entidades
 * y eventos: cualquier log que los imprima, también los de Spring MVC en DEBUG, muestra {@code ***} en lugar del valor.
 * Copia local de la misma utilidad de ms-security-util (sin dependencia entre repos).
 */
public final class Sensible {

    /** Texto que reemplaza a un valor sensible presente. */
    public static final String OCULTO = "***";

    private Sensible() {
    }

    /**
     * @param valor valor sensible
     * @return {@code "null"} si no hay valor; {@code ***} si lo hay (nunca el valor)
     */
    public static String ocultar(Object valor) {
        if (valor == null) {
            return "null";
        }
        return OCULTO;
    }
}
