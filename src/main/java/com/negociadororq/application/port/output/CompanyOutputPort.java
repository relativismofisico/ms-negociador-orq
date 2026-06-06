package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.Company;

import java.util.Optional;

public interface CompanyOutputPort {
    Optional<Company> findByNit(String nit);
}
