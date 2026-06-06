package com.negociadororq.application.service;

import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.application.port.output.FacturaOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Log
@Service
@RequiredArgsConstructor
public class ValidacionFacturaServiceImpl implements ValidacionFacturaService {

    private final FacturaOutputPort facturaPort;

    @Value("${negociacion.dias-minimos-vencimiento:10}")
    private long diasMinimosVencimiento;

    @Override
    public ValidacionFacturaResponseDto validarFacturaVencida(String numeroFactura, LocalDate fechaProceso) {

        LocalDate fechaValidacion = fechaProceso != null ? fechaProceso : LocalDate.now();

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

        return ValidacionFacturaResponseDto.builder()
                .valida(valida)
                .vencida(!valida)
                .diasHastaVencimiento(diasHastaVencimiento)
                .codigo(valida
                        ? "FACTURA_VIGENTE"
                        : "FACTURA_VENCIDA")
                .mensaje(valida
                        ? "Factura vigente para negociación/liquidación"
                        : "Factura no negociable. Restan "
                        + diasHastaVencimiento
                        + " días para vencimiento")
                .build();
    }
}

