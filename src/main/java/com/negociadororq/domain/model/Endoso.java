package com.negociadororq.domain.model;

import com.negociadororq.enums.EstadoEndoso;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "endosos")
@CompoundIndex(
        name = "uniq_negociacion_factura",
        def = "{'negociacionId': 1, 'numeroFactura': 1}",
        unique = true
)
public class Endoso {

    @Id
    private String id;

    private String negociacionId;
    private String numeroFactura;

    private String rutActorEndosa;
    private String rutProveedor; // quien endosa
    private String rutPagador;
    private String rutFondeador;
    private Operacion tipoOperacion;

    private EstadoEndoso estado; // PENDIENTE, ENDOSADO, RECHAZADO
    private String otp;

    /**
     * HISTORIAL COMPLETO DE APROBACIONES
     */
    private List<AprobacionActorDto> aprobaciones;
    private String documentoUrl; // enlace al PDF del endoso
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaAprobacion;
    private LocalDateTime fechaActualizacion;

    private String documentoHtml;
    private byte[] documentoPdf;
    private String nombreArchivoPdf;


}
