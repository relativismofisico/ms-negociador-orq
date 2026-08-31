package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.EmpresaQuota;

/**
 * Puerto de persistencia del cupo total de negociación asignado a una empresa.
 */
public interface EmpresaQuotaOutputPort {

    /**
     * @param companyId id de la empresa
     * @return el cupo de la empresa, si existe
     */
    Optional<EmpresaQuota> findByCompanyId(Long companyId);

    /**
     * @param quota cupo a persistir
     * @return el cupo persistido
     */
    EmpresaQuota save(EmpresaQuota quota);

    /**
     * Igual que {@link #save}, pero fuerza el flush inmediato a la base de
     * datos. Necesario cuando el cupo debe quedar consistente antes de
     * continuar con otra operación en la misma transacción.
     *
     * @param quota cupo a persistir
     * @return el cupo persistido
     */
    EmpresaQuota saveAndFlush(EmpresaQuota quota);
}
