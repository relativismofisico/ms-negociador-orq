package com.negociadororq.application.port.output;

public interface CompanyOperationOutputPort {
    boolean isEnabled(Long companyId, String operationType);
}
