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

    /**
     * Fase 28d: descuenta {@code valor} del disponible de la empresa solo si alcanza, en una sentencia.
     *
     * @param companyId empresa
     * @param valor     monto
     * @return true si se descontó
     */
    boolean descontar(Long companyId, java.math.BigDecimal valor);

    /**
     * Fase 28d (P-22): devuelve {@code valor} con tope en el techo, en una sentencia.
     *
     * @param companyId empresa
     * @param valor     monto
     * @return true si la empresa tiene cupo
     */
    boolean devolverConTope(Long companyId, java.math.BigDecimal valor);
}
