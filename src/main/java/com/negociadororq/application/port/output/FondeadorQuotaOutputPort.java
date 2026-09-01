package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.FondeadorQuota;

/**
 * Puerto de persistencia de los parámetros de negociación de un fondeador
 * ({@link com.negociadororq.domain.model.FondeadorQuota}).
 */
public interface FondeadorQuotaOutputPort {

    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @return la asignación fondeador-pagador, si existe
     */
    Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId);

    /**
     * @param funderId   id de la empresa fondeadora
     * @param supplierId id de la empresa proveedora
     * @return la asignación fondeador-proveedor, si existe
     */
    Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId);

    /**
     * Igual que {@link #findByFunderIdAndPayerId}, pero exige que la asignación
     * esté activa. Se usa al validar el fondeador elegido para una NUEVA
     * negociación: si el fondeador la desactivó, no debe poder usarse.
     */
    Optional<FondeadorQuota> findByFunderIdAndPayerIdActivo(Long funderId, Long payerId);

    /** Ver {@link #findByFunderIdAndPayerIdActivo}, para el lado proveedor. */
    Optional<FondeadorQuota> findByFunderIdAndSupplierIdActivo(Long funderId, Long supplierId);

    /**
     * @param quota asignación a persistir
     * @return la asignación persistida
     */
    FondeadorQuota save(FondeadorQuota quota);

    /**
     * Igual que {@link #save}, pero fuerza el flush inmediato a la base de
     * datos. Necesario para que un conflicto de optimistic locking
     * ({@code @Version}) se detecte en el momento del guardado, en vez de
     * diferirse al commit de la transacción (donde ya no se puede traducir a
     * una excepción de negocio dentro del propio método de servicio).
     *
     * @param quota asignación a persistir
     * @return la asignación persistida
     */
    FondeadorQuota saveAndFlush(FondeadorQuota quota);
}
