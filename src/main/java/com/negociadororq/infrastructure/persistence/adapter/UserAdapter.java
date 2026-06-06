package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.model.User;
import com.negociadororq.infrastructure.persistence.entity.UserEntity;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserAdapter implements UserOutputPort {

    private final UserRepository repository;

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username).map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {
        return User.builder()
                .ideUser(entity.getIdeUser())
                .username(entity.getUsername())
                .password(entity.getPassword())
                .createTime(entity.getCreateTime())
                .rolUserIde(entity.getRolUserIde())
                .companyIde(entity.getCompanyIde())
                .stateUserId(entity.getStateUserId())
                .personIde(entity.getPersonIde())
                .build();
    }
}
