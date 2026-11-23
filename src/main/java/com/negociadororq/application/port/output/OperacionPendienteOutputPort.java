package com.negociadororq.application.port.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Fase 28d, opción A: tabla única {@code operacion_pendiente} (MySQL). Se usa dentro de la transacción de MySQL de cada
 * flujo, junto con los cupos o la reversión.
 */
public interface OperacionPendienteOutputPort {

    /**
     * Alta idempotente: si ya existe una operación con ese tipo y clave, no escribe nada.
     *
     * @param tipo          tipo
     * @param claveNatural  clave natural ({@link OperacionPendiente#clave})
     * @param estadoInicial estado de alta
     * @param datos         datos mínimos para reanudarla, o null; nunca el OTP ni otra información sensible
     * @return {@code true} si la dio de alta; {@code false} si ya existía
     * @throws IllegalArgumentException si {@code datos} lleva un campo sensible
     *         ({@link OperacionPendiente#exigirDatosNoSensibles})
     */
    boolean registrar(TipoOperacionPendiente tipo, String claveNatural, String estadoInicial, String datos);

    /**
     * Fase 28d (A5b): si ya hay una creación viva (CUPOS_DESCONTADOS o COMPLETADA) para ese CUFE, en cualquier
     * negociación. Verificación rápida antes de descontar cupos; la definitiva es el compare-and-set de la factura.
     *
     * @param cufe CUFE de la factura
     * @return {@code true} si existe
     */
    boolean existeCreacionViva(String cufe);

    /**
     * Transición atómica (CAS): solo si la operación sigue en {@code desde}. Libera la concesión.
     *
     * @param tipo         tipo
     * @param claveNatural clave natural
     * @param desde        estado esperado
     * @param hacia        estado nuevo
     * @return si hizo la transición
     */
    boolean transicionar(TipoOperacionPendiente tipo, String claveNatural, String desde, String hacia);

    /**
     * @param tipo         tipo
     * @param claveNatural clave natural
     * @return la operación, si existe
     */
    Optional<OperacionPendiente> buscar(TipoOperacionPendiente tipo, String claveNatural);

    /**
     * Para el reconciliador: toma hasta {@code limite} operaciones en alguno de {@code estados}, sin cambios desde
     * {@code sinCambiosDesde} y sin concesión vigente; a cada una le pone la concesión hasta {@code leaseHasta} y suma
     * un intento. Dos reconciliadores no toman la misma.
     *
     * @param estados         estados a reanudar
     * @param sinCambiosDesde última transición anterior a este instante (gracia para el flujo en curso)
     * @param ahora           ahora
     * @param leaseHasta      fin de la concesión
     * @param limite          máximo a tomar
     * @return las tomadas
     */
    List<OperacionPendiente> tomarParaReanudar(List<String> estados, LocalDateTime sinCambiosDesde,
            LocalDateTime ahora, LocalDateTime leaseHasta, int limite);

    /**
     * Deja el último error (recortado, sin datos sensibles) y libera la concesión.
     *
     * @param id    operación
     * @param error descripción corta
     */
    void registrarError(Long id, String error);
}
