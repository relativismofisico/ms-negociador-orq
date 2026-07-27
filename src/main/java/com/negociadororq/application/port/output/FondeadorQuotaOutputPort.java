package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.FondeadorQuota;

import java.util.Optional;

public interface FondeadorQuotaOutputPort {
    Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId);
    Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId);

    /**
     * Igual que {@link #findByFunderIdAndPayerId}, pero exige que la asignación
     * esté activa. Se usa al validar el fondeador elegido para una NUEVA
     * negociación: si el fondeador la desactivó, no debe poder usarse.
     */
    Optional<FondeadorQuota> findByFunderIdAndPayerIdActivo(Long funderId, Long payerId);

    /** Ver {@link #findByFunderIdAndPayerIdActivo}, para el lado proveedor. */
    Optional<FondeadorQuota> findByFunderIdAndSupplierIdActivo(Long funderId, Long supplierId);

    FondeadorQuota save(FondeadorQuota quota);
}
