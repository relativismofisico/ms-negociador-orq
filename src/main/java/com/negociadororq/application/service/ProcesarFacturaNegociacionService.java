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
     * @param negociacionId  id de la negociación, generado antes de tocar cupos (Fase 28d, A2): clave del registro
     *                       exacto de lo descontado ({@code negociacion|cufe})
     * @return la factura ya procesada, con su detalle de negociación
     */
    FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest,
            String negociacionId);

    /**
     * Fase 28d (A2): marca la factura NEGOCIADA (estado de firma y actores) en Mongo. Lo llama la transacción Mongo de la
     * creación, junto con la negociación y su marcador; idempotente.
     *
     * @param request   datos de la negociación en curso
     * @param procesada la factura ya procesada (con su CUFE y fondeadores)
     */
    void marcarComoNegociada(NegociacionRequestDto request, FacturaNegociada procesada);
}

