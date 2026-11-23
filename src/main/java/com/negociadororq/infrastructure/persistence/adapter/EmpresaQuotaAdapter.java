package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.infrastructure.persistence.entity.EmpresaQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.EmpresaQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link EmpresaQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class EmpresaQuotaAdapter implements EmpresaQuotaOutputPort {

    private final EmpresaQuotaRepository repository;
    /** Fase 28d (D2): camino del techo con objetivo, con la fila bloqueada (misma transacción). */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public Optional<EmpresaQuota> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(this::toDomain);
    }

    @Override
    public EmpresaQuota save(EmpresaQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    @Override
    public EmpresaQuota saveAndFlush(EmpresaQuota quota) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(quota)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "Conflicto de optimistic locking al guardar EmpresaQuota id=" + quota.getId(), e);
        }
    }

    @Override
    public boolean descontar(Long companyId, java.math.BigDecimal valor) {
        return repository.descontar(companyId, valor) > 0;
    }

    @Override
    public boolean devolverConTope(Long companyId, java.math.BigDecimal valor) {
        // Camino común (sin objetivo): MIN(techo, disponible + devuelto) en una sentencia
        if (repository.devolverConTope(companyId, valor) > 0) {
            return true;
        }
        // Fase 28d (D2): cupo en reducción (objetivo): fórmula de John con la fila bloqueada, en la misma transacción
        java.util.List<java.util.Map<String, Object>> filas = jdbc.queryForList("SELECT quota, current_quota, objetivo"
                + " FROM parametrization WHERE company_id = ? FOR UPDATE", companyId);
        if (filas.isEmpty()) {
            return false;
        }
        java.util.Map<String, Object> f = filas.get(0);
        com.negociadororq.application.util.CupoConObjetivo.Resultado r =
                com.negociadororq.application.util.CupoConObjetivo.devolver((java.math.BigDecimal) f.get("quota"),
                        (java.math.BigDecimal) f.get("current_quota"), (java.math.BigDecimal) f.get("objetivo"), valor);
        jdbc.update("UPDATE parametrization SET quota = ?, current_quota = ?, objetivo = ?,"
                + " version = COALESCE(version, 0) + 1 WHERE company_id = ?", r.techo(), r.disponible(), r.objetivo(),
                companyId);
        return true;
    }

    private EmpresaQuota toDomain(EmpresaQuotaEntity entity) {
        return EmpresaQuota.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .version(entity.getVersion())
                .build();
    }

    private EmpresaQuotaEntity toEntity(EmpresaQuota quota) {
        return EmpresaQuotaEntity.builder()
                .id(quota.getId())
                .companyId(quota.getCompanyId())
                .quota(quota.getQuota())
                .currentQuota(quota.getCurrentQuota())
                .version(quota.getVersion())
                .build();
    }
}
