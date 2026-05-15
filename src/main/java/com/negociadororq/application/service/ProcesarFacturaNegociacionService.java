package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.domain.model.FacturaNegociada;

public interface ProcesarFacturaNegociacionService {
    FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest);
}
