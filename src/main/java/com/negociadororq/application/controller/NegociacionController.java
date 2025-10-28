package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.application.service.NegociacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/negociar")
@RequiredArgsConstructor
public class NegociacionController {

    private final NegociacionService negociacionService;

    @PostMapping("/factura")
    public ResponseEntity<NegociacionResponseDto> crearNegociacion(
            @RequestBody NegociacionRequestDto request) {

        NegociacionResponseDto response = negociacionService.crearNegociacion(request);
        return ResponseEntity.ok(response);
    }
}
