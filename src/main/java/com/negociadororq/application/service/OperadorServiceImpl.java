package com.negociadororq.application.service;

import com.negociadororq.domain.model.UserQuota;
import com.negociadororq.infrastructure.persistence.repository.EmpresaQuotaRepository;
import com.negociadororq.infrastructure.persistence.repository.UserQuotaRepository;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperadorServiceImpl implements OperadorService{

    private final UserQuotaRepository userQuotaRepository;
    private final EmpresaQuotaRepository empresaQuotaRepository;

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

    @Transactional
    @Override
    public void revertirCupo(Long userId, BigDecimal valor) {
        UserQuota cuota = userQuotaRepository.findByUser_IdeUser(userId)
                .orElseThrow(() -> new RuntimeException("No existe operador para el usuario ID " + userId));

        cuota.setCurrentQuota(cuota.getCurrentQuota().add(valor));
        userQuotaRepository.save(cuota);
    }

    @Override
    @Transactional
    public void revertirCupoUsuario(Long userId, BigDecimal valor) {

        userQuotaRepository.findByUser_IdeUser(userId)
                .ifPresent(uq -> {
                    uq.setCurrentQuota(
                            uq.getCurrentQuota()
                                    .add(valor)
                                    .max(BigDecimal.ZERO)
                    );
                });
    }

    @Override
    public void revertirCupoEmpresa(Long companyId, BigDecimal valor) {

        empresaQuotaRepository.findByCompanyId(companyId)
                .ifPresent(eq -> {
                    eq.setCurrentQuota(
                            eq.getCurrentQuota()
                                    .add(valor)
                                    .max(BigDecimal.ZERO)
                    );
                });
    }

}
