package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.UserQuota;

import java.util.Optional;

public interface UserQuotaOutputPort {
    Optional<UserQuota> findByUserId(Long userId);
    UserQuota save(UserQuota quota);
}
