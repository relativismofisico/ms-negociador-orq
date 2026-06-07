package com.negociadororq.application.dto;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Operacion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Solicitud para crear una negociación de Factoring o Confirming")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegociacionRequestDto {

    @Schema(description = "RUT o NIT de la empresa solicitante.", example = "900341104")
    private String rutEmpresa;

    @Schema(description = "Rol que ejerce la empresa en la operación.", example = "EMPRESA",
        allowableValues = {"EMPRESA", "PROVEEDOR", "PAGADOR"})
    private String rolEmpresa;

    @Schema(description = "Tipo y clase de la operación: FACTORING o CONFIRMING.")
    private Operacion tipoOperacion;

    @Schema(description = "Lista de facturas a negociar. Cada factura puede tener fondeadores propios.")
    private List<FacturaNegociada> facturas;

    @Schema(description = "Fondeadores aplicables a todas las facturas (opcional). "
        + "Si una factura tiene fondeadores propios, estos tienen precedencia.")
    private List<FondeadorAsignacion> fondeadoresGlobales;

    @Schema(description = "RUT o NIT del operador que procesa la negociación.", example = "1128384790")
    private String rutOperador;
}
