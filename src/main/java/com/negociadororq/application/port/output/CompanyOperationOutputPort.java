package com.negociadororq.application.port.output;

/**
 * Puerto de solo lectura para saber si una empresa tiene habilitado un tipo
 * de operación (p. ej. FACTORING, CONFIRMING). La habilitación en sí se
 * gestiona en ms-registroempresa-neg.
 */
public interface CompanyOperationOutputPort {

    /**
     * @param companyId     id de la empresa
     * @param operationType tipo de operación a verificar
     * @return true si la empresa tiene habilitado ese tipo de operación
     */
    boolean isEnabled(Long companyId, String operationType);
}
