package com.negociadororq.infrastructure.persistence.entity;

import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.enums.EstadoEndoso;
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
public class EndosoDocument {

    @Id
    private String id;
    private String negociacionId;
    private String numeroFactura;
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
