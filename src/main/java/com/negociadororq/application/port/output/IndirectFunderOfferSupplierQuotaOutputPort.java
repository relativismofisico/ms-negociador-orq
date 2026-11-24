package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;

/**
 * Puerto para el consumo independiente de cada proveedor contra una oferta
 * de cupo compartido fondeador→pagador. Cada fila se crea de forma perezosa
 * la primera vez que ese proveedor negocia contra esa oferta.
 */
public interface IndirectFunderOfferSupplierQuotaOutputPort {

    /**
     * Busca el consumo de un proveedor contra una oferta, si ya negoció antes.
     *
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @return consumo existente, si ya se creó
     */
    Optional<IndirectFunderOfferSupplierQuota> find(Long offerId, Long supplierId);

    /**
     * Crea la fila de consumo de un proveedor contra una oferta, con el cupo y
     * tasa vigentes de la oferta en ese instante (snapshot).
     *
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @param rate       tasa vigente de la oferta al crear
     * @param quota      cupo vigente de la oferta al crear (íntegro, no repartido)
     * @return el consumo recién creado
     * @throws com.negociadororq.application.exception.ConflictoException si otra
     *         negociación concurrente ya creó la fila del mismo par
     *         {@code (offerId, supplierId)} (choque del UNIQUE).
     */
    IndirectFunderOfferSupplierQuota crear(Long offerId, Long supplierId,
            java.math.BigDecimal rate, java.math.BigDecimal quota);

    /**
     * Descuenta {@code valor} del cupo disponible de la fila de consumo del par
     * {@code (offerId, supplierId)} de forma atómica en la base de datos. No hace
     * leer-modificar-escribir: dos descuentos concurrentes sobre la misma fila se
     * aplican ambos (la BD serializa el {@code SET current = current - valor}).
     *
     * @return filas afectadas — 0 si la fila de consumo todavía no existe.
     */
    int descontarCupo(Long offerId, Long supplierId, java.math.BigDecimal valor);

    /**
     * Fase 28d (A2): devuelve {@code valor} a la fila de consumo del par, de forma atómica
     * ({@code current_quota = current_quota + valor}); la inversa exacta de {@link #descontarCupo}.
     *
     * @param offerId    oferta indirecta
     * @param supplierId proveedor
     * @param valor      monto a devolver
     * @return filas afectadas (0 si la fila de consumo no existe)
     */
    int devolverCupo(Long offerId, Long supplierId, java.math.BigDecimal valor);

    /**
     * Fase 28d (P-20): recarga en una sentencia: el disponible vuelve al techo ({@code original_quota}).
     *
     * @param offerId    oferta
     * @param supplierId proveedor
     * @return filas afectadas (0 si la fila no existe)
     */
    int recargar(Long offerId, Long supplierId);

    /**
     * Persiste el consumo actualizado (tras descontar cupo).
     *
     * @param quota consumo con el cupo ya descontado
     * @return el consumo persistido
     */
    IndirectFunderOfferSupplierQuota save(IndirectFunderOfferSupplierQuota quota);

    /**
     * Fase 28d (P-19): resultado de propagar un cambio del cupo de la oferta.
     *
     * @param ajustadas filas ajustadas
     * @param porEncima filas que quedaron con un techo mayor que la plantilla (lo consumido no baja)
     */
    record Propagacion(int ajustadas, long porEncima) {
    }

    /**
     * Fase 28d (P-19, decisión de John J-09): ajusta a los proveedores que ya negociaron la oferta al cupo por proveedor
     * nuevo, en una transacción (ver la sentencia del repositorio).
     *
     * @param offerId oferta
     * @param version versión del cambio (cupo_version de la oferta)
     * @param nuevo   cupo por proveedor nuevo
     * @return filas ajustadas y cuántas quedaron por encima de la plantilla
     */
    Propagacion propagarCupoOferta(Long offerId, long version, java.math.BigDecimal nuevo);
}
