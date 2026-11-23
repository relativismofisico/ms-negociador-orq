package com.negociadororq.application.util;

import java.math.BigDecimal;

/**
 * Fase 28d (D2/D4, decisión de John): devolución de cupo sobre un "techo con objetivo". Con la fila bloqueada:
 * <pre>
 *   exposicion_nueva = MAX(techo - disponible - devuelto, 0)
 *   techo_nuevo      = objetivo IS NULL ? techo : MAX(objetivo, exposicion_nueva)
 *   disponible_nuevo = techo_nuevo - exposicion_nueva
 * </pre>
 * Sin objetivo equivale a {@code MIN(techo, disponible + devuelto)}. Cuando el techo llega al objetivo, el objetivo se
 * quita (ya no está "en reducción"). Todo exacto en BigDecimal.
 */
public final class CupoConObjetivo {

    /**
     * @param techo      techo nuevo
     * @param disponible disponible nuevo
     * @param objetivo   objetivo que queda ({@code null} si se alcanzó o no había)
     */
    public record Resultado(BigDecimal techo, BigDecimal disponible, BigDecimal objetivo) {

        /**
         * @param techoAnterior techo antes de la devolución
         * @return cuánto bajó el techo (0 si no bajó)
         */
        public BigDecimal bajaDelTecho(BigDecimal techoAnterior) {
            return techoAnterior.subtract(techo).max(BigDecimal.ZERO);
        }
    }

    private CupoConObjetivo() {
    }

    /**
     * @param techo      techo actual
     * @param disponible disponible actual
     * @param objetivo   objetivo actual, o {@code null}
     * @param devuelto   monto devuelto (positivo)
     * @return techo, disponible y objetivo nuevos
     */
    public static Resultado devolver(BigDecimal techo, BigDecimal disponible, BigDecimal objetivo, BigDecimal devuelto) {
        BigDecimal exposicion = techo.subtract(disponible).subtract(devuelto).max(BigDecimal.ZERO);
        BigDecimal techoNuevo = techo;
        BigDecimal objetivoNuevo = null;
        if (objetivo != null) {
            techoNuevo = objetivo.max(exposicion);
            if (techoNuevo.compareTo(objetivo) > 0) {
                objetivoNuevo = objetivo;
            }
        }
        return new Resultado(techoNuevo, techoNuevo.subtract(exposicion), objetivoNuevo);
    }
}
