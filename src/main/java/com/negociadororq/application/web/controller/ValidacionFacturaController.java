package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.ValidacionFacturaRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.dto.ErrorResponse;
import com.negociadororq.application.service.ValidacionFacturaService;
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

@Tag(name = "Validación de Facturas", description = "Consultas de estado y vencimiento de facturas")
@RestController
@RequestMapping("/validaciones/facturas")
@RequiredArgsConstructor
public class ValidacionFacturaController {

    private final ValidacionFacturaService validacionFacturaService;

    @Operation(
        summary = "Validar vencimiento de una factura",
        description = "Consulta si una factura se considera vencida a una fecha de proceso dada. "
            + "Operación de solo lectura: no modifica el estado de la factura.\n\n"
            + "**Autenticación soportada:**\n"
            + "- JWT Bearer token (roles: ADMINISTRADOR, OPERARIO, EMPRESA, FONDEADOR)\n"
            + "- HTTP Basic Auth con rutOperador (requiere rol OPERARIO)\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `OPERARIO`, `EMPRESA`, `FONDEADOR`",
        security = {
            @SecurityRequirement(name = "bearerAuth"),
            @SecurityRequirement(name = "basicAuth")
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado de la validación de vencimiento.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ValidacionFacturaResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Autenticación ausente, inválida o expirada (JWT o Basic Auth).",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El usuario no tiene los roles requeridos para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Factura no encontrada.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'validacion')")
    @PostMapping("/vencimiento")
    public ResponseEntity<ValidacionFacturaResponseDto> validarFacturaVencida(
            @RequestBody ValidacionFacturaRequestDto request) {
        ValidacionFacturaResponseDto response = validacionFacturaService
                .validarFacturaVencida(request.getNumeroFactura(), request.getFechaProceso());
        return ResponseEntity.ok(response);
    }
}
