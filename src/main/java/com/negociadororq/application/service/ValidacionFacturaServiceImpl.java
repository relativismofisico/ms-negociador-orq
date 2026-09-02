package com.negociadororq.application.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Implementación de {@link ValidacionFacturaService}.
 */
@Log
@RequiredArgsConstructor
public class ValidacionFacturaServiceImpl implements ValidacionFacturaService {

    private final FacturaOutputPort facturaPort;
    private final ClockPort clock;
    private final long diasMinimosVencimiento;

    @Override
    public ValidacionFacturaResponseDto validarFacturaVencida(String numeroFactura, LocalDate fechaProceso) {

        LocalDate fechaValidacion;
        if (fechaProceso != null) {
            fechaValidacion = fechaProceso;
        } else {
            fechaValidacion = clock.now().toLocalDate();
        }

        Factura factura = facturaPort
                .findByNumeroFactura(numeroFactura)
                .orElseThrow(() ->
                        new RecursoNoEncontradoException("Factura no encontrada: " + numeroFactura, "FACTURA_NO_ENCONTRADA"));

        if (factura.getFechavencimientoFactura() == null) {

            return ValidacionFacturaResponseDto.builder()
                    .valida(false)
                    .vencida(true)
                    .codigo("SIN_FECHA_VENCIMIENTO")
                    .mensaje("Factura sin fecha de vencimiento")
                    .build();
        }

        long diasHastaVencimiento =
                ChronoUnit.DAYS.between(
                        fechaValidacion,
                        factura.getFechavencimientoFactura());

        boolean valida = diasHastaVencimiento > diasMinimosVencimiento;

        String codigo;
        String mensaje;
        if (valida) {
            codigo = "FACTURA_VIGENTE";
            mensaje = "Factura vigente para negociación/liquidación";
        } else {
            codigo = "FACTURA_VENCIDA";
            mensaje = "Factura no negociable. Restan " + diasHastaVencimiento + " días para vencimiento";
        }

        return ValidacionFacturaResponseDto.builder()
                .valida(valida)
                .vencida(!valida)
                .diasHastaVencimiento(diasHastaVencimiento)
                .codigo(codigo)
                .mensaje(mensaje)
                .build();
    }
}

