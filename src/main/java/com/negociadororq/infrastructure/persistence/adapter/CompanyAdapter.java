package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.domain.model.Company;
import com.negociadororq.infrastructure.persistence.entity.CompanyEntity;
import com.negociadororq.infrastructure.persistence.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link CompanyOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class CompanyAdapter implements CompanyOutputPort {

    private final CompanyRepository repository;

    @Override
    public Optional<Company> findByNit(String nit) {
        return repository.findByNit(nit).map(this::toDomain);
    }

    private Company toDomain(CompanyEntity entity) {
        String ciudad = null;
        if (entity.getCity() != null) {
            ciudad = entity.getCity().getCityName();
        }

        return Company.builder()
                .ideCompany(entity.getIdeCompany())
                .nameCompany(entity.getNameCompany())
                .nit(entity.getNit())
                .ciudad(ciudad)
                .build();
    }
}
