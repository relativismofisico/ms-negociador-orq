package com.negociadororq.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Solicitud para registrar un endoso de descuento sobre una negociación")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndosoRequestDto {

    @Schema(description = "Identificador de la negociación. Se inyecta automáticamente desde el path variable.",
        example = "6847a1c2e4b0d3f8a2c1e5b7", accessMode = Schema.AccessMode.READ_ONLY)
    private String idNegociacion;

    @Schema(description = "RUT o NIT del actor que realiza el endoso.", example = "900341104")
    private String rutActor;

    @Schema(description = "Números de factura a incluir en el endoso. No puede estar vacía.",
        example = "[\"FECI3566\", \"FECI3567\"]")
    private List<String> numerosFactura;
}
