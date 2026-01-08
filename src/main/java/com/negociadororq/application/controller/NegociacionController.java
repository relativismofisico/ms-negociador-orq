package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.application.service.NegociacionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/negociar")
@RequiredArgsConstructor
public class    NegociacionController {

    private final NegociacionService negociacionService;


    /**
     * Permite negociar las facturas que estan disponibles (estadoFirma="SIN_NEGOCIAR") dependiendo
     * del tipo de operacion enviado al momento de la consulta
     ** @param request dto de negociacion.
     * @return  NegociacionResponseDto lista de facturas negociadas
     *
     * */
    @Operation(summary = "Permite negociar las facturas disponibles en el sistema por tipo de operacion")
    @PostMapping("/factura")
    public ResponseEntity<NegociacionResponseDto> crearNegociacion(
            @RequestBody NegociacionRequestDto request) {

        NegociacionResponseDto response = negociacionService.crearNegociacion(request);
        return ResponseEntity.ok(response);
    }
}
