package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.ActualizarCupoPropioRequestDto;
import com.negociadororq.application.dto.CupoPropioFondeoDto;
import com.negociadororq.application.exception.dto.ErrorResponse;
import com.negociadororq.application.service.CupoPropioFondeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cupo propio de fondeo", description = "Cupo de autofondeo que una empresa aprobada para Confirming define para sí misma")
@RestController
@RequestMapping("/companies/{companyId}/cupo-propio-fondeo")
@RequiredArgsConstructor
public class CupoPropioFondeoController {

    private final CupoPropioFondeoService cupoPropioFondeoService;

    @Operation(
        summary = "Consultar el cupo propio de autofondeo de la empresa",
        description = "Retorna el cupo de autofondeo actual de la empresa junto con el tope máximo "
            + "asignado por el administrador.\n\n**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cupo propio retornado exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @GetMapping
    public ResponseEntity<CupoPropioFondeoDto> obtenerCupoPropio(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId) {
        return ResponseEntity.ok(cupoPropioFondeoService.obtenerCupoPropio(companyId));
    }

    @Operation(
        summary = "Definir el cupo propio de autofondeo de la empresa",
        description = "La empresa define su propio cupo de autofondeo para Confirming. No puede exceder "
            + "el cupo general asignado por el administrador de la plataforma.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cupo propio actualizado exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "El cupo propio solicitado excede el cupo asignado por el administrador.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @PutMapping
    public ResponseEntity<CupoPropioFondeoDto> actualizarCupoPropio(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId,
            @Valid @RequestBody ActualizarCupoPropioRequestDto request) {
        return ResponseEntity.ok(cupoPropioFondeoService.actualizarCupoPropio(companyId, request.getCupoPropio()));
    }
}
