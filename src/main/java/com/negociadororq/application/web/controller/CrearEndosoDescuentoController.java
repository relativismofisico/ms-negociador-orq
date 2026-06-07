package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.dto.ErrorResponse;
import com.negociadororq.application.service.EndosoDescuentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Endosos", description = "Registro de endosos de descuento sobre negociaciones")
@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class CrearEndosoDescuentoController {

    private final EndosoDescuentoService endosoDescuentoService;

    @Operation(
        summary = "Registrar endoso de descuento",
        description = "Registra un endoso de descuento sobre las facturas indicadas de una negociación existente. "
            + "Genera el documento HTML del endoso para su posterior firma digital.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `OPERARIO`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Endoso registrado exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EndosoResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado o lista de facturas vacía.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Negociación no encontrada por el ID indicado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regla de negocio violada (ej: factura no endosable, endoso duplicado).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'endoso')")
    @PostMapping("/{idNegociacion}/endosos")
    public ResponseEntity<EndosoResponseDto> registrarEndoso(
            @Parameter(description = "Identificador único de la negociación en MongoDB.", required = true, example = "NEG-001")
            @PathVariable String idNegociacion,
            @RequestBody EndosoRequestDto request) {

        request.setIdNegociacion(idNegociacion);
        EndosoResponseDto response = endosoDescuentoService.registrarEndoso(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
