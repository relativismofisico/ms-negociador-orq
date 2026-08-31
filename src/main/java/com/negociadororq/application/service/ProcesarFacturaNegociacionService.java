package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.domain.model.FacturaNegociada;

/**
 * Procesa una factura individual dentro de una negociación: valida su
 * elegibilidad y calcula el detalle de la negociación para esa factura.
 */
public interface ProcesarFacturaNegociacionService {

    /**
     * @param request        datos de la negociación en curso
     * @param facturaRequest datos de la factura a procesar
     * @return la factura ya procesada, con su detalle de negociación
     */
    FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest);
}

