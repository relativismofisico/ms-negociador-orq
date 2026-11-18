package com.negociadororq.application.service;

import java.time.LocalDate;

import com.negociadororq.application.dto.ValidacionFacturaRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.domain.model.Factura;

/** Valida si una factura está vencida a una fecha de proceso dada. */
public interface ValidacionFacturaService {
    /**
     * Fase 28d (C): la factura se identifica por CUFE o por número y RUT ({@code ResolutorFactura#resolverConsulta}),
     * exactamente una.
     *
     * @param request número, y opcionalmente CUFE y RUT de proveedor y pagador, y fecha de proceso
     * @return resultado de la validación, indicando si la factura está vencida
     */
    ValidacionFacturaResponseDto validarFacturaVencida(ValidacionFacturaRequestDto request);

    /**
     * Determina si una factura está vencida comparando su fecha de vencimiento contra la fecha de proceso indicada.
     *
     * @param factura      factura ya identificada
     * @param fechaProceso fecha contra la que se evalúa el vencimiento, o null (hoy)
     * @return resultado de la validación, indicando si la factura está vencida
     */
    ValidacionFacturaResponseDto validarFacturaVencida(Factura factura, LocalDate fechaProceso);
}

