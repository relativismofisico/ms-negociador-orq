package com.negociadororq.infrastructure.persistence.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.Operacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistencia MongoDB de un {@link com.negociadororq.domain.model.Endoso}. El índice
 * único evita registrar dos veces el mismo endoso para la misma combinación de
 * negociación y facturas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "endosos")
@CompoundIndex(
        name = "uniq_negociacion_factura",
        def = "{'negociacionId': 1, 'numerosFactura': 1}",
        unique = true
)
public class EndosoDocument {

    @Id
    private String id;
    private String negociacionId;
    private List<String> numerosFactura;
    private String rutActorEndosa;
    private String rutProveedor;
    private String rutPagador;
    private String rutFondeador;
    private Operacion tipoOperacion;
    private EstadoEndoso estado;
    private String otp;
    private List<AprobacionActorDto> aprobaciones;
    private String documentoUrl;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaAprobacion;
    private LocalDateTime fechaActualizacion;
    private String documentoHtml;
    private byte[] documentoPdf;
    private String nombreArchivoPdf;
}
