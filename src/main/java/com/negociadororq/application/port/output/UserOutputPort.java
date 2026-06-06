package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.User;

import java.util.Optional;

public interface UserOutputPort {
    Optional<User> findByUsername(String username);
}
