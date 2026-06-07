package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.exception.dto.ErrorResponse;
import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.application.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Aprobación OTP", description = "Flujo de aprobación de endosos mediante código OTP")
@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class AprobarEndosoDescuentoController {

    private final AprobacionEndosoDescuentoService service;

    @Operation(
        summary = "Solicitar código OTP para aprobación",
        description = "Dispara el envío de un código OTP al actor responsable de aprobar el endoso. "
            + "El OTP se entrega por el canal configurado (generalmente Kafka → notificación). "
            + "La respuesta es **202 Accepted** indicando que la solicitud fue recibida y está en procesamiento asíncrono.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `FONDEADOR`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Solicitud de OTP recibida y en procesamiento.",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string", example = "OTP solicitado"))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Negociación no encontrada.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'otp')")
    @PostMapping("/solicitar-otp")
    public ResponseEntity<String> solicitarOtp(@RequestBody AprobacionRequestDto request) {
        service.solicitarOtp(request);
        return ResponseEntity.accepted().body("OTP solicitado");
    }

    @Operation(
        summary = "Confirmar OTP y aprobar endoso",
        description = "Valida el código OTP recibido y, si es correcto, aprueba el endoso de descuento. "
            + "La IP del cliente se captura automáticamente para auditoría. "
            + "La respuesta es **202 Accepted**: la validación final ocurre de forma asíncrona vía Kafka.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `FONDEADOR`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "OTP recibido y validación en procesamiento.",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string", example = "Validando OTP..."))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Negociación no encontrada.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "OTP inválido o expirado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'otp')")
    @PostMapping("/confirmar-otp")
    public ResponseEntity<String> confirmarOtp(
            @RequestBody ConfirmacionOtpRequestDto request,
            HttpServletRequest httpRequest) {
        String ip = IpUtils.obtenerIp(httpRequest);
        request.setIpAprobacion(ip);
        service.confirmarOtp(request);
        return ResponseEntity.accepted().body("Validando OTP...");
    }
}
