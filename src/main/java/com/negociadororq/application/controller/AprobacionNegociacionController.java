package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;
import com.negociadororq.application.service.AprobacionNegociacionService;
import com.negociadororq.application.service.NegociacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class AprobacionNegociacionController {

    private final AprobacionNegociacionService aprobacionNegociacionService;
    private final NegociacionService negociacionService;

    @PostMapping("/aprobacion")
    public ResponseEntity<String> aprobarFacturas(
            @RequestBody AprobacionRequestDto request
    ) {
        negociacionService.aprobarFactura(
                request.getIdNegociacion(),
                request.getRutEmpresa(),
                request.getFacturas()
        );
        return ResponseEntity.ok("Facturas aprobadas/rechazadas correctamente");
    }
}
