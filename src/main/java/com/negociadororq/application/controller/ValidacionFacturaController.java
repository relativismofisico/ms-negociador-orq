package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.ValidacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.service.ValidacionFacturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/validaciones/facturas")
@RequiredArgsConstructor
public class ValidacionFacturaController {

    private final ValidacionFacturaService validacionFacturaService;

    // Roles autorizados configurados en application.yml → security.roles.validacion
    @PreAuthorize("@sec.tieneRol(authentication, 'validacion')")
    @PostMapping("/vencimiento")
    public ResponseEntity<ValidacionFacturaResponseDto> validarFacturaVencida(
            @RequestBody ValidacionFacturaRequestDto request) {
        ValidacionFacturaResponseDto response = validacionFacturaService
                .validarFacturaVencida(request.getNumeroFactura(), request.getFechaProceso());
        return ResponseEntity.ok(response);
    }
}
