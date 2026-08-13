package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.TasaConversionRequestDto;
import com.negociadororq.application.dto.TasaConversionResponseDto;
import com.negociadororq.application.exception.dto.ErrorResponse;
import com.negociadororq.application.service.TasaConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Conversión de tasas",
    description = "Convierte una tasa entre Tasa Efectiva Anual, Tasa Mes Vencido y Tasa Mes Anticipado, "
        + "para que cada empresa pueda ver una tasa que le informaron en la unidad que prefiera")
@RestController
@RequestMapping("/tasas")
@RequiredArgsConstructor
public class TasaConversionController {

    private final TasaConversionService tasaConversionService;

    @Operation(
        summary = "Convertir una tasa entre Efectiva Anual, Mes Vencido y Mes Anticipado",
        description = "Operación de solo lectura, sin efectos secundarios: recibe un valor y el tipo de tasa "
            + "en el que está expresado, y devuelve sus equivalentes en los tres tipos soportados.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversión calculada",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TasaConversionResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Valor o tipo de tasa inválido",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Autenticación ausente, inválida o expirada",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El usuario no tiene los roles requeridos",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'conversion-tasa')")
    @PostMapping("/conversion")
    public ResponseEntity<TasaConversionResponseDto> convertir(@RequestBody TasaConversionRequestDto request) {
        return ResponseEntity.ok(tasaConversionService.convertir(request.getValor(), request.getTipoTasa()));
    }
}
