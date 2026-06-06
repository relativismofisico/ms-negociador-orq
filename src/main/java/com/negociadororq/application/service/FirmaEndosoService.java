package com.negociadororq.application.service;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.enums.RolActor;

public interface FirmaEndosoService {
    void firmarPdfEndoso(String idNegociacion, FacturaNegociada factura, PendingOtp pending, String rutEmpresa,
            String rutOperador, RolActor rolActor, boolean aprobado, String observacion);
}

