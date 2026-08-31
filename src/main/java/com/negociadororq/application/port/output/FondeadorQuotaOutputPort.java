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
}
