package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.domain.model.UserQuota;
import com.negociadororq.infrastructure.persistence.entity.UserQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.UserQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link UserQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class UserQuotaAdapter implements UserQuotaOutputPort {

    private final UserQuotaRepository repository;
    /** Fase 28d (D4): camino del techo con objetivo, con la fila bloqueada (misma transacción). */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public Optional<UserQuota> findByUserId(Long userId) {
        return repository.findByUser_IdeUser(userId).map(this::toDomain);
    }

    @Override
    public UserQuota save(UserQuota quota) {
        UserQuotaEntity entity = repository.findById(quota.getId())
                .orElseThrow(() -> new RuntimeException("UserQuota no encontrada id=" + quota.getId()));
        entity.setCurrentQuota(quota.getCurrentQuota());
        entity.setQuota(quota.getQuota());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public boolean descontar(Long userId, java.math.BigDecimal monto) {
        return repository.descontar(userId, monto) == 1;
    }

    @Override
    @Transactional
    public boolean devolver(Long userId, java.math.BigDecimal monto, boolean sinNegativos) {
        // Camino común (sin objetivo): MIN(techo, disponible + devuelto) en una sentencia
        int filas;
        if (sinNegativos) {
            filas = repository.devolverSinNegativos(userId, monto);
        } else {
            filas = repository.devolver(userId, monto);
        }
        return filas == 1 || devolverConObjetivo(userId, monto);
    }

    /**
     * Fase 28d (D4): operador con objetivo (p. ej. desactivado, objetivo 0): fórmula de John con la fila bloqueada; lo que
     * baje el techo baja también la suma de techos de su empresa ({@code parametrization.assigned_quota}), en la misma
     * transacción (base compartida).
     */
    private boolean devolverConObjetivo(Long userId, java.math.BigDecimal monto) {
        java.util.List<java.util.Map<String, Object>> filas = jdbc.queryForList("SELECT quota, current_quota, objetivo"
                + " FROM user_quota WHERE user_id = ? FOR UPDATE", userId);
        if (filas.isEmpty()) {
            return false;
        }
        java.util.Map<String, Object> f = filas.get(0);
        java.math.BigDecimal techo = (java.math.BigDecimal) f.get("quota");
        java.math.BigDecimal disponible = ((java.math.BigDecimal) f.get("current_quota")).max(java.math.BigDecimal.ZERO);
        com.negociadororq.application.util.CupoConObjetivo.Resultado r =
                com.negociadororq.application.util.CupoConObjetivo.devolver(techo, disponible,
                        (java.math.BigDecimal) f.get("objetivo"), monto.max(java.math.BigDecimal.ZERO));
        jdbc.update("UPDATE user_quota SET quota = ?, current_quota = ?, objetivo = ? WHERE user_id = ?", r.techo(),
                r.disponible(), r.objetivo(), userId);
        java.math.BigDecimal baja = r.bajaDelTecho(techo);
        if (baja.signum() > 0) {
            jdbc.update("UPDATE parametrization p JOIN `user` u ON u.company_ide = p.company_id"
                    + " SET p.assigned_quota = GREATEST(p.assigned_quota - ?, 0), p.version = COALESCE(p.version, 0) + 1"
                    + " WHERE u.ide_user = ?", baja, userId);
        }
        return true;
    }

    private UserQuota toDomain(UserQuotaEntity entity) {
        Long userId = null;
        if (entity.getUser() != null) {
            userId = entity.getUser().getIdeUser();
        }

        return UserQuota.builder()
                .id(entity.getId())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .userId(userId)
                .build();
    }
}
