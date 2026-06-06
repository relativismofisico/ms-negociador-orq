package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.domain.model.FacturaNegociada;

public interface ProcesarFacturaNegociacionService {
    FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest);
}

