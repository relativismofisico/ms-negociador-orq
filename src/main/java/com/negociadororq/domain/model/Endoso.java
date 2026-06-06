package com.negociadororq.domain.model;

import com.negociadororq.domain.enums.EstadoEndoso;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Endoso {

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
