package com.negociadororq.web.controller;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;
import com.negociadororq.application.service.NegociacionFactoringComfirmingService;
import com.negociadororq.web.exception.ErrorResponse;
import com.negociadororq.web.security.CompanyOwnershipGuard;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creación de negociaciones de Factoring y Confirming.
 */
@Tag(name = "Negociación", description = "Creación de negociaciones de Factoring y Confirming")
@RestController
@RequestMapping("/negociar")
@RequiredArgsConstructor
public class CrearNegociacionFactoringComfirmingController {

    private final NegociacionFactoringComfirmingService negociacionFactoringComfirmingService;

    private final CompanyOwnershipGuard companyOwnership;

    @Operation(
        summary = "Crear negociación Factoring / Confirming",
        description = "Inicia una nueva negociación procesando las facturas indicadas. "
            + "Valida cupo de operador, empresa y fondeadores. "
            + "Las facturas sin cupo quedan como NO_NEGOCIADAS y se informan en `errores`.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `OPERARIO`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Negociación creada. El estado puede ser PENDIENTE o RECHAZADA.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = NegociacionResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado o JSON inválido.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Operador o factura no encontrada.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regla de negocio violada (ej: cupo insuficiente, factura vencida).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'negociacion')")
    @PostMapping("/factura")
    public ResponseEntity<NegociacionResponseDto> crearNegociacion(
            @RequestBody NegociacionRequestDto request) {

        companyOwnership.exigirActorEsLlamador(request.getRutEmpresa(), request.getRutOperador());
        NegociacionResponseDto response = negociacionFactoringComfirmingService.crearNegociacion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
