package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.IndirectFunderOfferSupplierQuotaDto;
import com.negociadororq.application.service.IndirectFunderOfferSupplierQuotaConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cupo de factoring indirecto vía pagador",
        description = "Consulta de solo lectura del cupo disponible de un proveedor contra una oferta de "
                + "cupo compartido fondeador→pagador")
@RestController
@RequestMapping("/companies/{supplierId}/indirect-funder-offers")
@RequiredArgsConstructor
public class IndirectFunderOfferSupplierQuotaController {

    private final IndirectFunderOfferSupplierQuotaConsultaService consultaService;

    @Operation(
        summary = "Consulta el cupo disponible de un proveedor contra una oferta de fondeo indirecta",
        description = "Devuelve 200 con cuerpo vacío si el pagador no habilitó la visibilidad o no hay oferta "
                + "aprobada vigente con ese fondeador. No crea la fila de consumo (eso solo ocurre al negociar).",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta exitosa")
    })
    @PreAuthorize("@sec.tieneRol(authentication, 'negociacion')")
    @GetMapping("/{funderId}/{payerId}/cupo")
    public ResponseEntity<IndirectFunderOfferSupplierQuotaDto> obtenerCupo(
            @Parameter(description = "Id de la empresa proveedora", required = true) @PathVariable Long supplierId,
            @Parameter(description = "Id de la empresa fondeadora", required = true) @PathVariable Long funderId,
            @Parameter(description = "Id de la empresa pagadora", required = true) @PathVariable Long payerId) {

        return ResponseEntity.ok(consultaService.obtenerCupo(funderId, payerId, supplierId));
    }
}
