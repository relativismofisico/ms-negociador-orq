package com.negociadororq.application.port.output;

/**
 * Excepción propia del contrato de los puertos de persistencia que usan
 * optimistic locking ({@code @Version}), lanzada por su adaptador de
 * infraestructura cuando detecta que la fila fue modificada por otra
 * petición concurrente entre la lectura y el guardado.
 *
 * <p>Existe para que application/ no dependa directamente de la excepción
 * real de Spring Data ({@code ObjectOptimisticLockingFailureException}) —
 * el adaptador de infraestructura es el único que la conoce, y la traduce
 * a esta antes de que cruce hacia application/.</p>
 */
public class OptimisticLockConflictException extends RuntimeException {

    /**
     * @param mensaje mensaje técnico describiendo qué guardado entró en conflicto
     * @param causa   la excepción real de la capa de persistencia
     */
    public OptimisticLockConflictException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
