package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort;
import com.negociadororq.infrastructure.persistence.entity.CompanySectionVerificationEntity;
import com.negociadororq.infrastructure.persistence.repository.CompanySectionVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompanySectionVerificationAdapter implements CompanySectionVerificationOutputPort {

    private static final List<String> TESORERIA_SECTION_KEYS = List.of("POLITICAS_PAGO_TESORERIA", "CUENTAS_BANCARIAS");

    private final CompanySectionVerificationRepository repository;

    @Override
    public boolean tesoreriaYCuentasVerificadas(Long companyId) {
        List<String> sectionKeysVerificadas = repository
                .findByCompanyIdAndSectionKeyIn(companyId, TESORERIA_SECTION_KEYS)
                .stream()
                .map(CompanySectionVerificationEntity::getSectionKey)
                .toList();

        return sectionKeysVerificadas.containsAll(TESORERIA_SECTION_KEYS);
    }
}
