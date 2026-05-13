package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.ValidacionFacturaResponseDto;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidacionFacturaServiceImpl implements ValidacionFacturaService {

    private final FacturaRepository facturaRepository;

    @Value("${negociacion.dias-minimos-vencimiento:10}")
    private long diasMinimosVencimiento;

    @Override
    public ValidacionFacturaResponseDto validarFacturaVencida(String numeroFactura, LocalDate fechaProceso) {

        LocalDate fechaValidacion = fechaProceso != null ? fechaProceso : LocalDate.now();

        Factura factura = facturaRepository
                .findByNumeroFactura(numeroFactura)
                .orElseThrow(() ->
                        new RuntimeException("Factura no encontrada: " + numeroFactura));

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
