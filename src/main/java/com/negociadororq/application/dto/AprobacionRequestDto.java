package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Schema(description = "Solicitud para iniciar el flujo de aprobación OTP sobre una negociación")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionRequestDto {

    @Schema(description = "Identificador MongoDB de la negociación a aprobar.", example = "6847a1c2e4b0d3f8a2c1e5b7")
    private String idNegociacion;

    @Schema(description = "RUT o NIT del operador que gestiona la aprobación.", example = "1128384790")
    private String rutOperador;

    @Schema(description = "RUT o NIT del actor que aprueba (pagador, fondeador o proveedor).", example = "900341104")
    private String rutEmpresa;

    @Schema(description = "Rol del actor aprobador en la operación.", example = "FONDEADOR",
        allowableValues = {"ADMINISTRADOR", "EMPRESA", "FONDEADOR", "PROVEEDOR", "PAGADOR"})
    private String rolActor;

    @Schema(description = "Lista de facturas con su decisión individual de aprobación o rechazo.")
    private List<AprobacionFacturaRequestDto> facturas;

    @Schema(description = "Observación opcional del aprobador.", example = "Aprobado previa revisión de documentos.")
    private String observacion;
}
