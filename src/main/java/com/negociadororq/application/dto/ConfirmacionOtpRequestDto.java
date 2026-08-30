package com.negociadororq.application.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para confirmar un código OTP y aprobar el endoso.
 */
@Schema(description = "Solicitud para confirmar un código OTP y aprobar el endoso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacionOtpRequestDto {

    @Schema(description = "Identificador MongoDB de la negociación.", example = "6847a1c2e4b0d3f8a2c1e5b7")
    private String idNegociacion;

    @Schema(description = "RUT o NIT de la empresa aprobadora.", example = "900341104")
    private String rutEmpresa;

    @Schema(description = "RUT o NIT del operador que gestiona la confirmación.", example = "1128384790")
    private String rutOperador;

    @Schema(description = "Código OTP de 6 dígitos recibido por el canal de notificación.", example = "482931")
    private String otp;

    @Schema(description = "IP del cliente que confirma el OTP. Se inyecta automáticamente desde el request HTTP.",
        accessMode = Schema.AccessMode.READ_ONLY)
    private String ipAprobacion;

    @Schema(description = "Lista de facturas con su decisión de aprobación individual.")
    private List<AprobacionFacturaRequestDto> facturas;
}
