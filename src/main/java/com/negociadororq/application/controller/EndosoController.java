package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import com.negociadororq.application.service.EndosoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class EndosoController {

    private final EndosoService endosoService;

    @PostMapping("/{idNegociacion}/endosos")
    public ResponseEntity<EndosoResponseDto> registrarEndoso(
            @PathVariable String idNegociacion,
            @RequestBody EndosoRequestDto request) {

        request.setIdNegociacion(idNegociacion);
        EndosoResponseDto response = endosoService.registrarEndoso(request);
        return ResponseEntity.ok(response);
    }
}
