package com.negociadororq.application.dto;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Operacion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Resultado de la creación de una negociación")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegociacionResponseDto {

    @Schema(description = "Identificador único de la negociación generado por MongoDB.", example = "6847a1c2e4b0d3f8a2c1e5b7")
    private String idNegociacion;

    @Schema(description = "RUT o NIT de la empresa de la negociación.", example = "900341104")
    private String rutEmpresa;

    @Schema(description = "Tipo y clase de la operación.")
    private Operacion tipoOperacion;

    @Schema(description = "Facturas que fueron aceptadas y procesadas en la negociación.")
    private List<FacturaNegociada> facturas;

    @Schema(description = "Fondeadores asignados a la negociación.")
    private List<FondeadorAsignacion> fondeadoresAsignados;

    @Schema(description = "Suma del valor de las facturas aceptadas.", example = "5000000.00")
    private BigDecimal totalNegociado;

    @Schema(description = "Estado final de la negociación.", example = "PENDIENTE",
        allowableValues = {"PENDIENTE", "ACEPTADA", "RECHAZADA"})
    private String estado;

    @Schema(description = "Mensajes de error para facturas rechazadas. Vacío si todas fueron aceptadas.")
    private List<String> errores;
}
