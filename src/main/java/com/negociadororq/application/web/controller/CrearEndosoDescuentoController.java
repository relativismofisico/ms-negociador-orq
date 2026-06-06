package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.service.EndosoDescuentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class CrearEndosoDescuentoController {

    private final EndosoDescuentoService endosoDescuentoService;

    @PreAuthorize("@sec.tieneRol(authentication, 'endoso')")
    @PostMapping("/{idNegociacion}/endosos")
    public ResponseEntity<EndosoResponseDto> registrarEndoso(
            @PathVariable String idNegociacion,
            @RequestBody EndosoRequestDto request) {

        request.setIdNegociacion(idNegociacion);
        EndosoResponseDto response = endosoDescuentoService.registrarEndoso(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
