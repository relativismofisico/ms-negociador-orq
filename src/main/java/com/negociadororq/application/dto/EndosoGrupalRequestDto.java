package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para permitir o exigir endoso/solicitud individual por factura.
 */
@Schema(description = "Solicitud para permitir o exigir endoso/solicitud individual por factura")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoGrupalRequestDto {

    @Schema(description = "true para permitir agrupar varias facturas en una sola solicitud, "
        + "false para exigir una solicitud por factura", example = "false")
    @NotNull
    private Boolean endosoGrupal;
}
