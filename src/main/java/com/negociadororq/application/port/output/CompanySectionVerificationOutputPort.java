package com.negociadororq.application.port.output;

/**
 * Puerto de solo lectura para saber si una empresa completó y verificó las
 * secciones de tesorería y cuentas bancarias, requisito para negociar.
 */
public interface CompanySectionVerificationOutputPort {

    /**
     * @param companyId id de la empresa
     * @return true si tesorería y cuentas bancarias están verificadas
     */
    boolean tesoreriaYCuentasVerificadas(Long companyId);
}
