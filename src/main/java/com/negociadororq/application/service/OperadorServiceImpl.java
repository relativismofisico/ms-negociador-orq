package com.negociadororq.application.service;

import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.UserQuota;
import com.negociadororq.infrastructure.persistence.repository.EmpresaQuotaRepository;
import com.negociadororq.infrastructure.persistence.repository.FondeadorQuotaRepository;
import com.negociadororq.infrastructure.persistence.repository.UserQuotaRepository;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperadorServiceImpl implements OperadorService{

    private final UserQuotaRepository userQuotaRepository;
    private final EmpresaQuotaRepository empresaQuotaRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;

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
                                    .max(BigDecimal.ZERO));});
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertirCupoEmpresa(Long companyId,
                                    BigDecimal valor) {

        EmpresaQuota empresaQuota =
                empresaQuotaRepository
                        .findByCompanyId(companyId)
                        .orElseThrow();

        valor = valor.abs();

        BigDecimal actual = empresaQuota.getCurrentQuota();

        BigDecimal nuevo = actual.add(valor);

        if (nuevo.compareTo(empresaQuota.getQuota()) > 0) {
            nuevo = empresaQuota.getQuota();
        }
        empresaQuota.setCurrentQuota(nuevo);

        empresaQuotaRepository.saveAndFlush(empresaQuota);

    }

    @Override
    @Transactional
    public void revertirCupoFondeador(Long funderId, Long payerId, BigDecimal valor) {

        FondeadorQuota quota = fondeadorQuotaRepository
                .findByFunderIdAndPayerId(funderId, payerId)
                .orElseThrow(() ->
                        new RuntimeException("No existe cupo para funderId=" + funderId + " payerId=" + payerId));

        BigDecimal cupoActual = Optional.ofNullable(quota.getPayerCurrentQuota()).orElse(BigDecimal.ZERO);

        quota.setPayerCurrentQuota(cupoActual.add(valor));

        fondeadorQuotaRepository.save(quota);
    }

}
