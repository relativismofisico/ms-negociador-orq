package com.negociadororq.web.controller;

import com.negociadororq.application.dto.ActualizarCupoPropioRequestDto;
import com.negociadororq.application.dto.CupoPropioFondeoDto;
import com.negociadororq.application.dto.EndosoGrupalRequestDto;
import com.negociadororq.application.dto.RentabilidadMinimaRequestDto;
import com.negociadororq.application.service.CupoPropioFondeoService;
import com.negociadororq.web.exception.ErrorResponse;
import com.negociadororq.web.security.CompanyOwnershipGuard;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cupo de autofondeo que una empresa aprobada para Confirming define para sí misma.
 */
@Tag(name = "Cupo propio de fondeo", description = "Cupo de autofondeo que una empresa aprobada para Confirming define para sí misma")
@RestController
@RequestMapping("/companies/{companyId}/cupo-propio-fondeo")
@RequiredArgsConstructor
public class CupoPropioFondeoController {

    private final CupoPropioFondeoService cupoPropioFondeoService;

    private final CompanyOwnershipGuard companyOwnership;

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
        companyOwnership.exigirEmpresaPropia(companyId);
        return ResponseEntity.ok(cupoPropioFondeoService.obtenerCupoPropio(companyId));
    }

    @Operation(
        summary = "Definir el cupo propio de autofondeo de la empresa",
        description = "La empresa define su propio cupo de autofondeo para Confirming. No puede exceder "
            + "el cupo general asignado por el administrador de la plataforma. Debe indicar además el tipo "
            + "de descuento (FIJO, FINANCIERO o DINAMICO); FINANCIERO y DINAMICO exigen además el tipo de "
            + "tasa; DINAMICO exige además la tarifa plena (coexiste con tasa). También debe indicar la "
            + "vigencia del descuento (TODO_EL_PLAZO, PRIMEROS_DIAS o DIAS_ANTES_VENCIMIENTO); las dos "
            + "últimas exigen además los días de vigencia.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cupo propio actualizado exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado, o tipoDescuento/"
            + "tipoTasa con un valor no soportado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token JWT ausente, expirado o con firma inválida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "El rol del token no está autorizado para este endpoint.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "El cupo propio solicitado excede el cupo asignado por el "
            + "administrador, falta el tipo de tasa cuando el tipo de descuento lo exige, o falta la tarifa "
            + "plena cuando el tipo de descuento es DINAMICO.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @PutMapping
    public ResponseEntity<CupoPropioFondeoDto> actualizarCupoPropio(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId,
            @Valid @RequestBody ActualizarCupoPropioRequestDto request) {
        companyOwnership.exigirEmpresaPropia(companyId);
        return ResponseEntity.ok(
                cupoPropioFondeoService.actualizarCupoPropio(companyId, request.getCupoPropio(), request.getTasa(),
                        request.getTipoDescuento(), request.getTipoTasa(), request.getTarifaPlena(),
                        request.getVigenciaDescuento(), request.getDiasVigencia(), request.getPorcentajeReserva()));
    }

    @Operation(
        summary = "Permitir o exigir endoso/solicitud individual por factura en el autofondeo",
        description = "Con endosoGrupal=true la empresa puede agrupar varias facturas autofondeadas en una sola "
            + "solicitud de descuento; con false (por defecto) debe hacer una por factura.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuración actualizada exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @PatchMapping("/endoso-grupal")
    public ResponseEntity<CupoPropioFondeoDto> actualizarEndosoGrupal(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId,
            @Valid @RequestBody EndosoGrupalRequestDto request) {
        companyOwnership.exigirEmpresaPropia(companyId);
        return ResponseEntity.ok(
                cupoPropioFondeoService.actualizarEndosoGrupal(companyId, Boolean.TRUE.equals(request.getEndosoGrupal())));
    }

    @Operation(
        summary = "Activar o desactivar la rentabilidad mínima esperada del autofondeo",
        description = "La empresa pagadora-fondeadora puede exigirse a sí misma una rentabilidad mínima "
            + "esperada, expresada como tasa Efectivo Anual (EA), para sus operaciones autofondeadas de "
            + "Confirming.\n\n**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuración actualizada exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Se activó sin indicar el valor de rentabilidad mínima.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @PatchMapping("/rentabilidad-minima")
    public ResponseEntity<CupoPropioFondeoDto> actualizarRentabilidadMinima(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId,
            @Valid @RequestBody RentabilidadMinimaRequestDto request) {
        companyOwnership.exigirEmpresaPropia(companyId);
        return ResponseEntity.ok(cupoPropioFondeoService.actualizarRentabilidadMinima(
                companyId, Boolean.TRUE.equals(request.getActiva()), request.getValor()));
    }

    @Operation(
        summary = "Restablecer el cupo disponible del autofondeo",
        description = "Libera el cupo consumido: vuelve el cupo disponible al valor del cupo propio "
            + "configurado, sin modificar el cupo propio, la tasa ni el resto de la configuración.\n\n"
            + "**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cupo restablecido exitosamente.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CupoPropioFondeoDto.class))),
        @ApiResponse(responseCode = "404", description = "La empresa no está habilitada como fondeadora de sí misma, "
            + "o el administrador aún no le asignó un cupo general.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'cupo-propio')")
    @PatchMapping("/restablecer")
    public ResponseEntity<CupoPropioFondeoDto> restablecerCupo(
            @Parameter(description = "Identificador de la empresa", example = "1", required = true)
            @PathVariable Long companyId) {
        companyOwnership.exigirEmpresaPropia(companyId);
        return ResponseEntity.ok(cupoPropioFondeoService.restablecerCupo(companyId));
    }
}
