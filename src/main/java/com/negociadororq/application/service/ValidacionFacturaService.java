package com.negociadororq.application.service;

import java.time.LocalDate;

import com.negociadororq.application.dto.ValidacionFacturaResponseDto;

/** Valida si una factura está vencida a una fecha de proceso dada. */
public interface ValidacionFacturaService {
    /**
     * Determina si una factura está vencida comparando su fecha de vencimiento contra la
     * fecha de proceso indicada.
     *
     * @param numeroFactura número de la factura a validar
     * @param fechaProceso  fecha contra la que se evalúa el vencimiento
     * @return resultado de la validación, indicando si la factura está vencida
     */
    ValidacionFacturaResponseDto validarFacturaVencida(String numeroFactura, LocalDate fechaProceso);
}

