package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.Company;

/**
 * Puerto de solo lectura para datos de empresas, replicados desde
 * ms-registroempresa-neg.
 */
public interface CompanyOutputPort {

    /**
     * @param nit NIT de la empresa
     * @return la empresa, si existe
     */
    Optional<Company> findByNit(String nit);
}
