package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Decisión de aprobación individual para una factura dentro de la solicitud OTP")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionFacturaRequestDto {

    @Schema(description = "Número identificador de la factura.", example = "FECI3566")
    private String numeroFactura;

    @Schema(description = "`true` si el actor aprueba la factura, `false` si la rechaza.", example = "true")
    private boolean aprobado;

    @Schema(description = "Observación opcional sobre la decisión de esta factura.", example = "Sin observaciones.")
    private String observacion;
}
