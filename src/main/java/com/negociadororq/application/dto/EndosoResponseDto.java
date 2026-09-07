package com.negociadororq.application.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado del registro de un endoso de descuento.
 */
@Schema(description = "Resultado del registro de un endoso de descuento")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoResponseDto {

    @Schema(description = "Identificador de la negociación asociada al endoso.", example = "6847a1c2e4b0d3f8a2c1e5b7")
    private String idNegociacion;

    @Schema(description = "Estado del endoso registrado.", example = "PENDIENTE",
        allowableValues = {"PENDIENTE", "ENDOSADO", "RECHAZADO"})
    private String estadoEndoso;

    @Schema(description = "Números de factura incluidos exitosamente en el endoso.")
    private List<String> facturasEndosadas;

    @Schema(description = "CUFE (clave única global) de cada factura endosada exitosamente. "
        + "Array paralelo a facturasEndosadas: mismo orden y tamaño. Un elemento puede ser "
        + "null si la factura no tiene CUFE registrado.")
    private List<String> cufesEndosadas;

    @Schema(description = "Números de factura que no pudieron ser endosadas.")
    private List<String> facturasRechazadas;

    @Schema(description = "Contenido HTML del documento de endoso generado para firma digital.",
        accessMode = Schema.AccessMode.READ_ONLY)
    private String plantillaHtml;
}
