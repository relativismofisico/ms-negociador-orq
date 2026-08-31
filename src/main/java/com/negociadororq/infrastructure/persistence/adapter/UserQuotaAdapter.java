package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.domain.model.UserQuota;
import com.negociadororq.infrastructure.persistence.entity.UserQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.UserQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link UserQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class UserQuotaAdapter implements UserQuotaOutputPort {

    private final UserQuotaRepository repository;

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
