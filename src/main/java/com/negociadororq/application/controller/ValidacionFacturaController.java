package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.ValidacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.service.ValidacionFacturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/validaciones/facturas")
@RequiredArgsConstructor
public class ValidacionFacturaController {

    private final ValidacionFacturaService validacionFacturaService;

    // 200 OK: operación de consulta/validación — no crea recurso, devuelve resultado del análisis
    @PostMapping("/vencimiento")
    public ResponseEntity<ValidacionFacturaResponseDto> validarFacturaVencida(
            @RequestBody ValidacionFacturaRequestDto request) {
        ValidacionFacturaResponseDto response = validacionFacturaService
                .validarFacturaVencida(request.getNumeroFactura(), request.getFechaProceso());
        return ResponseEntity.ok(response);
    }
}