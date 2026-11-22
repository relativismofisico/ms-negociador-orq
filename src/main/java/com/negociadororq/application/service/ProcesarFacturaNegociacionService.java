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
     * Fase 28d (validación acumulada del cupo indirecto): como {@link #procesarFactura(NegociacionRequestDto,
     * FacturaNegociada, String)}, con la suma de la petición y el disponible inicial de cada trío para el rechazo.
     *
     * @param request        datos de la negociación en curso
     * @param facturaRequest datos de la factura a procesar
     * @param negociacionId  id de la negociación
     * @param contexto       tríos de cupo indirecto de la petición ({@link #prepararCupoIndirecto})
     * @return la factura ya procesada
     */
    default FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest,
            String negociacionId, ContextoCupoIndirecto contexto) {
        return procesarFactura(request, facturaRequest, negociacionId);
    }

    /**
     * Fase 28d: antes de procesar la primera factura, suma por trío (fondeador, pagador, proveedor) las facturas de la
     * petición que van por cupo indirecto y lee el disponible de cada trío. Solo lectura.
     *
     * @param request    datos de la negociación (sus facturas, en el orden de la petición)
     * @param facturasBd las facturas de la base, en el mismo orden que {@code request.getFacturas()}
     * @return el contexto (vacío si ninguna va por cupo indirecto)
     */
    default ContextoCupoIndirecto prepararCupoIndirecto(NegociacionRequestDto request,
            java.util.List<com.negociadororq.domain.model.Factura> facturasBd) {
        return ContextoCupoIndirecto.vacio();
    }

    /**
     * Fase 28d (A2): marca la factura NEGOCIADA (estado de firma y actores) en Mongo. Lo llama la transacción Mongo de la
     * creación, junto con la negociación y su marcador; idempotente.
     *
     * @param request   datos de la negociación en curso
     * @param procesada la factura ya procesada (con su CUFE y fondeadores)
     */
    void marcarComoNegociada(NegociacionRequestDto request, FacturaNegociada procesada);
}

