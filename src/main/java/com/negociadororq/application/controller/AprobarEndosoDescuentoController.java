package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.application.service.NegociacionFactoringComfirmingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class AprobarEndosoDescuentoController {

    private final AprobacionEndosoDescuentoService aprobacionEndosoDescuentoService;

    @PostMapping("/aprobacion")
    public ResponseEntity<String> aprobarFacturas(@RequestBody AprobacionRequestDto request) {

        aprobacionEndosoDescuentoService.aprobar(request);

        return ResponseEntity.ok("Facturas aprobadas/rechazadas correctamente");
    }
}
