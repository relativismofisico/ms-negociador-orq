package com.negociadororq.application.service;

import com.negociadororq.domain.model.UserQuota;
import com.negociadororq.domain.model.User;
import com.negociadororq.infrastructure.repository.UserQuotaRepository;
import com.negociadororq.infrastructure.repository.UserRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperadorServiceImpl implements OperadorService{

    private final UserQuotaRepository userQuotaRepository;
    private final UserRepository userRepository;

    @Override
    public BigDecimal obtenerCupoDisponible(Long userId) {
        return userQuotaRepository.findByUser_IdeUser(userId)
                .map(UserQuota::getCurrentQuota)
                .orElseThrow(() -> new RuntimeException("No existe operador para el usuario ID " + userId));

    }

    @Override
    public void descontarCupo(Long userId, BigDecimal monto) {
        UserQuota cuota = userQuotaRepository.findByUser_IdeUser(userId)
                .orElseThrow(() -> new RuntimeException("No existe operador para el usuario ID " + userId));

        if (cuota.getCurrentQuota().compareTo(monto) < 0) {
            throw new RuntimeException("Cupo insuficiente para el operador " + userId);
        }

        cuota.setCurrentQuota(cuota.getCurrentQuota().subtract(monto));
        userQuotaRepository.save(cuota);
    }
}
