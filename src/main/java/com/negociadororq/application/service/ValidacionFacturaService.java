package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.ValidacionFacturaResponseDto;

import java.time.LocalDate;

public interface ValidacionFacturaService{
    ValidacionFacturaResponseDto validarFacturaVencida(String numeroFactura, LocalDate fechaProceso);
}
