package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.CompanyOperationOutputPort;
import com.negociadororq.infrastructure.persistence.repository.CompanyOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link CompanyOperationOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class CompanyOperationAdapter implements CompanyOperationOutputPort {

    private final CompanyOperationRepository repository;

    @Override
    public boolean isEnabled(Long companyId, String operationType) {
        return repository.findByCompanyIdAndOperationTypeAndEnabledTrue(companyId, operationType).isPresent();
    }
}
