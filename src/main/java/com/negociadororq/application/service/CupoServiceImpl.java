package com.negociadororq.application.service;

import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.infrastructure.repository.CompanyRepository;
import com.negociadororq.infrastructure.repository.EmpresaQuotaRepository;
import com.negociadororq.infrastructure.repository.FondeadorQuotaRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CupoServiceImpl implements CupoService{

    private final EmpresaQuotaRepository empresaQuotaRepository;
    private final FondeadorQuotaRepository fondeadorQuotaRepository;
    private final CompanyRepository companyRepository;

    /**
     * Descuenta del cupo total de la empresa el valor negociado.
     */@Override
    @Transactional
    public void descontarCupoEmpresa(Long companyId, BigDecimal montoNegociado) {
        EmpresaQuota empresaQuota = empresaQuotaRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalArgumentException("No existe cupo para la empresa ID " + companyId));

        BigDecimal nuevoCupo = empresaQuota.getCurrentQuota().subtract(montoNegociado);
        if (nuevoCupo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("La empresa no tiene suficiente cupo para negociar este monto");
        }

        // SOLO cambiamos currentQuota
        empresaQuota.setCurrentQuota(nuevoCupo);

        // opcional: repository.save(empresaQuota); si estás dentro de @Transactional no es obligatorio,
        // pero puedes dejarlo para claridad:
        empresaQuotaRepository.save(empresaQuota);
    }

    /**
     * Descuenta del cupo del fondeador el valor negociado según el pagador y proveedor.
     */
    @Override
    @Transactional
    public void descontarCupoFondeador(String rutFondeador, String rutProveedor, String rutPagador, BigDecimal montoNegociado) {
        try {Long fondeadorId = companyRepository.findByNit(rutFondeador)
                .map(Company::getIdeCompany)
                .orElseThrow(() -> new IllegalArgumentException("Fondeador no encontrado con NIT " + rutFondeador));

            Long proveedorId = companyRepository.findByNit(rutProveedor)
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado con NIT " + rutProveedor));

            Long pagadorId = companyRepository.findByNit(rutPagador)
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new IllegalArgumentException("Pagador no encontrado con NIT " + rutPagador));

            // Busca cupo configurado entre fondeador y pagador
            FondeadorQuota fondeadorQuota = fondeadorQuotaRepository
                    .findByFunderIdAndPayerId(fondeadorId, pagadorId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe cupo configurado entre el fondeador y el pagador (funderId="
                                    + fondeadorId + ", payerId=" + pagadorId + ")"));

            BigDecimal nuevoCupoPayer = fondeadorQuota.getPayerCurrentQuota().subtract(montoNegociado);

            if (nuevoCupoPayer.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("El fondeador no tiene suficiente cupo disponible para esta negociación");
            }

            fondeadorQuota.setPayerCurrentQuota(nuevoCupoPayer);
            fondeadorQuotaRepository.save(fondeadorQuota);

            log.info("Cupo actualizado correctamente para fondeadorId={}, pagadorId={}, nuevoCupo={}",
                    fondeadorId, pagadorId, nuevoCupoPayer);

        } catch (Exception e) {
            log.error("Error al descontar cupo del fondeador: {}", e.getMessage(), e);
            throw e;
        }
    }

}
