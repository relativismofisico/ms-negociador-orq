package com.negociadororq.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositorio Spring Data JPA de {@link IndirectFunderOfferSupplierQuotaEntity}.
 */
public interface IndirectFunderOfferSupplierQuotaRepository
        extends JpaRepository<IndirectFunderOfferSupplierQuotaEntity, Long> {

    /**
     * @param offerId    id de la oferta indirecta
     * @param supplierId id de la empresa proveedora
     * @return el consumo de ese proveedor contra esa oferta, si existe
     */
    Optional<IndirectFunderOfferSupplierQuotaEntity> findByOfferIdAndSupplierId(Long offerId, Long supplierId);

    /**
     * Descuenta {@code valor} del cupo disponible de la fila de consumo del par
     * {@code (offerId, supplierId)} de forma atómica en la base de datos
     * ({@code current_quota = current_quota - valor}), sin leer-modificar-escribir:
     * dos negociaciones concurrentes del mismo par ya no pueden pisarse el
     * resultado.
     *
     * <p>Fase 28d (A5b, auditoría de escrituras de cupo): el control de cupo suficiente va en la MISMA sentencia
     * ({@code current_quota >= valor}); antes estaba solo en una lectura previa y dos descuentos a la vez dejaban el cupo
     * negativo.</p>
     *
     * @return número de filas afectadas — 0 si la fila de consumo no existe o no tiene cupo suficiente.
     */
    @Modifying
    @Transactional
    @Query("UPDATE IndirectFunderOfferSupplierQuotaEntity e "
            + "SET e.currentQuota = e.currentQuota - :valor "
            + "WHERE e.offerId = :offerId AND e.supplierId = :supplierId AND e.currentQuota >= :valor")
    int descontarCupo(@Param("offerId") Long offerId,
                      @Param("supplierId") Long supplierId,
                      @Param("valor") BigDecimal valor);

    /**
     * Fase 28d (A2): la inversa exacta de {@link #descontarCupo}, atómica en la base de datos; P-22: con tope en el techo
     * ({@code original_quota}, el valor que restaura la recarga): tras una recarga con exposición viva, devolver no lo
     * supera.
     *
     * @return número de filas afectadas — 0 si la fila de consumo no existe.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET current_quota = LEAST(original_quota, current_quota + :valor)"
            + " WHERE offer_id = :offerId AND supplier_id = :supplierId", nativeQuery = true)
    int devolverCupo(@Param("offerId") Long offerId,
                     @Param("supplierId") Long supplierId,
                     @Param("valor") BigDecimal valor);

    /**
     * Fase 28d (P-20): recarga en UNA sentencia: el disponible vuelve al techo ({@code original_quota}). Antes se leía la
     * fila y se guardaba entera con JPA, y lo que otro escribiera entre la lectura y el guardado se perdía.
     *
     * @return filas afectadas (0 si la fila no existe)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET current_quota = original_quota"
            + " WHERE offer_id = :offerId AND supplier_id = :supplierId", nativeQuery = true)
    int recargar(@Param("offerId") Long offerId, @Param("supplierId") Long supplierId);

    /**
     * Fase 28d (P-19, condición de John): orden de bloqueos único en todos los caminos del cupo indirecto: primero la
     * OFERTA (compartido) y después la fila del proveedor. Registro toma la oferta en exclusivo al cambiar la plantilla y
     * no toca filas de proveedor: no hay ciclo posible.
     *
     * @return la oferta, si existe
     */
    @Query(value = "SELECT id FROM indirect_funder_offer WHERE id = :offerId LOCK IN SHARE MODE", nativeQuery = true)
    java.util.List<Long> bloquearOferta(@Param("offerId") Long offerId);

    /**
     * Fase 28d (P-19): crea la fila del proveedor con la plantilla y la versión de la oferta LEÍDAS EN ESTA SENTENCIA, con
     * bloqueo compartido sobre la fila de la oferta: un cambio de plantilla en curso espera a que esta fila exista, y su
     * evento la alcanza. Techo, disponible y quota parten de la plantilla.
     *
     * @return filas creadas (0 si la oferta no existe)
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, rate, quota, current_quota,"
            + " original_quota, oferta_cupo_plantilla, oferta_cupo_version)"
            + " SELECT o.id, :supplierId, :rate, o.supplier_quota_template, o.supplier_quota_template,"
            + " o.supplier_quota_template, o.supplier_quota_template, o.cupo_version"
            + " FROM indirect_funder_offer o WHERE o.id = :offerId LOCK IN SHARE MODE", nativeQuery = true)
    int crearDesdeOferta(@Param("offerId") Long offerId, @Param("supplierId") Long supplierId,
            @Param("rate") BigDecimal rate);

    /**
     * Fase 28d (P-19): filas creadas por un negociador anterior a P-19 (sin plantilla): su plantilla es su techo, el
     * valor de la oferta con el que nacieron.
     *
     * @return filas completadas
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET oferta_cupo_plantilla = original_quota"
            + " WHERE offer_id = :offerId AND oferta_cupo_plantilla IS NULL", nativeQuery = true)
    int completarPlantilla(@Param("offerId") Long offerId);

    /**
     * Fase 28d (P-19, decisión de John J-09): ajuste de los proveedores que ya negociaron la oferta, en UNA sentencia
     * relativa por fila. Con d = :nuevo - oferta_cupo_plantilla y consumido = original_quota - current_quota:
     * techo_nuevo = MAX(techo + d, consumido) y disponible_nuevo = techo_nuevo - consumido = MAX(disponible + d, 0).
     * MySQL asigna de izquierda a derecha: original_quota usa el disponible y la plantilla anteriores, current_quota
     * también (aún no cambiaron) y quota toma el techo nuevo. Solo filas con versión menor: una reentrega o un evento
     * viejo que llega después no cambia nada.
     *
     * @return filas ajustadas
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE indirect_funder_offer_supplier_quota SET"
            + " original_quota = original_quota - current_quota + GREATEST(current_quota + :nuevo - oferta_cupo_plantilla, 0),"
            + " current_quota = GREATEST(current_quota + :nuevo - oferta_cupo_plantilla, 0),"
            + " quota = original_quota,"
            + " oferta_cupo_plantilla = :nuevo,"
            + " oferta_cupo_version = :version"
            + " WHERE offer_id = :offerId AND oferta_cupo_version < :version AND oferta_cupo_plantilla IS NOT NULL",
            nativeQuery = true)
    int propagarCupoOferta(@Param("offerId") Long offerId, @Param("version") long version,
            @Param("nuevo") BigDecimal nuevo);

    /**
     * Fase 28d (P-19): proveedores de la oferta que, tras una bajada, quedaron con un techo mayor que la plantilla (lo
     * consumido no baja): el "exceso" se reporta en el log.
     *
     * @return cuántos
     */
    @Query(value = "SELECT COUNT(*) FROM indirect_funder_offer_supplier_quota WHERE offer_id = :offerId"
            + " AND original_quota > :plantilla", nativeQuery = true)
    long contarPorEncimaDe(@Param("offerId") Long offerId, @Param("plantilla") BigDecimal plantilla);

    /**
     * Fase 28d (P-19, decisión de John): persiste el exceso de las filas que ESTE cambio acaba de ajustar (versión igual)
     * y quedaron con un techo mayor que la plantilla nueva (lo consumido no baja): consumido, plantilla nueva, exceso y
     * versión. INSERT IGNORE sobre (oferta, proveedor, versión): una reentrega no duplica.
     *
     * @return filas de exceso registradas
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO cupo_oferta_exceso (offer_id, supplier_id, consumido, plantilla_nueva, exceso,"
            + " version, registrado_en)"
            + " SELECT offer_id, supplier_id, original_quota - current_quota, :nuevo, original_quota - :nuevo, :version,"
            + " NOW(6) FROM indirect_funder_offer_supplier_quota"
            + " WHERE offer_id = :offerId AND oferta_cupo_version = :version AND original_quota > :nuevo",
            nativeQuery = true)
    int registrarExceso(@Param("offerId") Long offerId, @Param("version") long version,
            @Param("nuevo") BigDecimal nuevo);
}
