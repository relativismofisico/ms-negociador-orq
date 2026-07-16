package com.negociadororq.application.service;

import com.negociadororq.application.dto.CupoPropioFondeoDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CupoPropioFondeoServiceImpl implements CupoPropioFondeoService {

    private final FondeadorQuotaOutputPort fondeadorQuotaPort;
    private final EmpresaQuotaOutputPort empresaQuotaPort;

    @Override
    public CupoPropioFondeoDto obtenerCupoPropio(Long companyId) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        return toDto(companyId, autofondeo, empresaQuota);
    }

    @Override
    public CupoPropioFondeoDto actualizarCupoPropio(Long companyId, BigDecimal nuevoCupo) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        if (nuevoCupo.compareTo(empresaQuota.getQuota()) > 0) {
            throw new ReglaDeNegocioException(
                    "El cupo propio de fondeo no puede exceder el cupo asignado por el administrador",
                    "CUPO_PROPIO_EXCEDE_CUPO_ADMINISTRADOR");
        }

        // Preserva el monto ya consumido: el disponible se recalcula sobre el nuevo
        // cupo en vez de sumarle la diferencia, para no regalar ni penalizar cupo
        // por el simple hecho de editar el límite.
        BigDecimal cupoAnterior = Optional.ofNullable(autofondeo.getPayerQuota()).orElse(BigDecimal.ZERO);
        BigDecimal disponibleAnterior = Optional.ofNullable(autofondeo.getPayerCurrentQuota()).orElse(BigDecimal.ZERO);
        BigDecimal consumido = cupoAnterior.subtract(disponibleAnterior).max(BigDecimal.ZERO);

        BigDecimal nuevoDisponible = nuevoCupo.subtract(consumido).max(BigDecimal.ZERO);

        autofondeo.setPayerQuota(nuevoCupo);
        autofondeo.setPayerCurrentQuota(nuevoDisponible);

        FondeadorQuota guardado = fondeadorQuotaPort.save(autofondeo);

        return toDto(companyId, guardado, empresaQuota);
    }

    private FondeadorQuota buscarAutofondeo(Long companyId) {
        return fondeadorQuotaPort.findByFunderIdAndPayerId(companyId, companyId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa " + companyId
                                + " no está habilitada como fondeadora de sí misma para Confirming"));
    }

    private EmpresaQuota buscarCupoAdministrador(Long companyId) {
        return empresaQuotaPort.findByCompanyId(companyId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El administrador aún no ha asignado un cupo general a la empresa " + companyId));
    }

    private CupoPropioFondeoDto toDto(Long companyId, FondeadorQuota autofondeo, EmpresaQuota empresaQuota) {
        return CupoPropioFondeoDto.builder()
                .companyId(companyId)
                .cupoPropio(Optional.ofNullable(autofondeo.getPayerQuota()).orElse(BigDecimal.ZERO))
                .cupoDisponible(Optional.ofNullable(autofondeo.getPayerCurrentQuota()).orElse(BigDecimal.ZERO))
                .cupoMaximoAdministrador(empresaQuota.getQuota())
                .build();
    }
}
