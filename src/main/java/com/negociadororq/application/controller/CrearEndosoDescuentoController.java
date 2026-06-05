package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import com.negociadororq.application.service.EndosoDescuentoService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * Permite realizar el endoso de una o varias facturas que estan disponibles en una negociacion
     ** @param idNegociacion ide de la negociacion.
     ** @param request dto de la negociacion.
     * @return  EndosoResponseDto json con las facturas a las que se le realizo el endoso con su respectivo html
     *
     * */
    @Operation(summary = "Permite realizar el endoso de una o varias fcturas de una negociacion")
    // 201 CREATED: se persisten nuevos recursos Endoso en MongoDB
    // ⚠ RIESGO DE COMPATIBILIDAD: clientes que esperan 200 deben actualizarse a aceptar 201
    @PostMapping("/{idNegociacion}/endosos")
    public ResponseEntity<EndosoResponseDto> registrarEndoso(
            @PathVariable String idNegociacion,
            @RequestBody EndosoRequestDto request) {

        request.setIdNegociacion(idNegociacion);
        EndosoResponseDto response = endosoDescuentoService.registrarEndoso(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}