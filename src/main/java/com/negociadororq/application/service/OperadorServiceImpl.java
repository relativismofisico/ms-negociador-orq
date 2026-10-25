package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.UserQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link CupoConsumidorService} y {@link CupoReversionService}
 * (agregadas en {@link OperadorService}).
 */
@Log
@RequiredArgsConstructor
public class OperadorServiceImpl implements CupoConsumidorService, CupoReversionService {

    private final UserQuotaOutputPort userQuotaPort;
    private final EmpresaQuotaOutputPort empresaQuotaPort;
    private final FondeadorQuotaOutputPort fondeadorQuotaPort;

    @Override
    public BigDecimal obtenerCupoDisponible(Long userId) {
        return userQuotaPort.findByUserId(userId)
                .map(UserQuota::getCurrentQuota)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe operador para el usuario ID " + userId));
    }

    @Override
    public void descontarCupo(Long userId, BigDecimal monto) {
        UserQuota cuota = userQuotaPort.findByUserId(userId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe operador para el usuario ID " + userId));

        if (cuota.getCurrentQuota().compareTo(monto) < 0) {
            throw new ReglaDeNegocioException("Cupo insuficiente para el operador " + userId);
        }

        cuota.setCurrentQuota(cuota.getCurrentQuota().subtract(monto));
        userQuotaPort.save(cuota);
    }

    @Transactional
    @Override
    public void revertirCupo(Long userId, BigDecimal valor) {
        UserQuota cuota = userQuotaPort.findByUserId(userId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe operador para el usuario ID " + userId));

        cuota.setCurrentQuota(cuota.getCurrentQuota().add(valor));
        userQuotaPort.save(cuota);
    }

    @Override
    @Transactional
    public void revertirCupoUsuario(Long userId, BigDecimal valor) {
        userQuotaPort.findByUserId(userId)
                .ifPresent(uq -> {
                    uq.setCurrentQuota(
                            uq.getCurrentQuota()
                                    .add(valor)
                                    .max(BigDecimal.ZERO));
                    userQuotaPort.save(uq);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertirCupoEmpresa(Long companyId, BigDecimal valor) {
        EmpresaQuota empresaQuota = empresaQuotaPort.findByCompanyId(companyId).orElseThrow();

        valor = valor.abs();

        BigDecimal actual = empresaQuota.getCurrentQuota();
        BigDecimal nuevo = actual.add(valor);

        if (nuevo.compareTo(empresaQuota.getQuota()) > 0) {
            nuevo = empresaQuota.getQuota();
        }
        empresaQuota.setCurrentQuota(nuevo);

        empresaQuotaPort.saveAndFlush(empresaQuota);
    }

    @Override
    @Transactional
    public void revertirCupoFondeadorConfirming(Long funderId, Long payerId, BigDecimal valor) {
        FondeadorQuota quota = fondeadorQuotaPort.findByFunderIdAndPayerId(funderId, payerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe cupo confirming para funderId=" + funderId + " payerId=" + payerId));

        BigDecimal actual = Optional.ofNullable(quota.getPayerCurrentQuota()).orElse(BigDecimal.ZERO);
        BigDecimal nuevo = actual.add(valor.abs());

        if (quota.getPayerQuota() != null && nuevo.compareTo(quota.getPayerQuota()) > 0) {
            nuevo = quota.getPayerQuota();
        }

        quota.setPayerCurrentQuota(nuevo);
        // saveAndFlush (no save): mismo motivo que revertirCupoEmpresa arriba — fuerza
        // que un conflicto de @Version se detecte de forma síncrona en vez de diferirse.
        fondeadorQuotaPort.saveAndFlush(quota);

        log.info(String.format("Cupo confirming revertido funderId=%s payerId=%s valor=%s", funderId, payerId, valor));
    }

    @Override
    @Transactional
    public void revertirCupoFondeadorFactoring(Long funderId, Long supplierId, BigDecimal valor) {
        FondeadorQuota quota = fondeadorQuotaPort.findByFunderIdAndSupplierId(funderId, supplierId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe cupo factoring para funderId=" + funderId + " supplierId=" + supplierId));

        BigDecimal actual = Optional.ofNullable(quota.getSupplierCurrentQuota()).orElse(BigDecimal.ZERO);
        BigDecimal nuevo = actual.add(valor.abs());

        if (quota.getSupplierQuota() != null && nuevo.compareTo(quota.getSupplierQuota()) > 0) {
            nuevo = quota.getSupplierQuota();
        }

        quota.setSupplierCurrentQuota(nuevo);
        // saveAndFlush (no save): mismo motivo que revertirCupoEmpresa arriba — fuerza
        // que un conflicto de @Version se detecte de forma síncrona en vez de diferirse.
        fondeadorQuotaPort.saveAndFlush(quota);

        log.info(String.format("Cupo factoring revertido funderId=%s supplierId=%s valor=%s", funderId, supplierId, valor));
    }

}

