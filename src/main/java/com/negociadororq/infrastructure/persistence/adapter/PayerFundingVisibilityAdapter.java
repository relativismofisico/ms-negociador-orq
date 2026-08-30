package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.infrastructure.persistence.repository.PayerFundingVisibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link PayerFundingVisibilityOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class PayerFundingVisibilityAdapter implements PayerFundingVisibilityOutputPort {

    private final PayerFundingVisibilityRepository repository;

    @Override
    public boolean estaHabilitado(Long payerId) {
        return repository.findByCompanyIdAndHabilitadoTrue(payerId).isPresent();
    }
}
