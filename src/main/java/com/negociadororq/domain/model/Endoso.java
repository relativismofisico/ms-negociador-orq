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

    /**
     * Facturas cubiertas por esta solicitud de endoso/descuento. Con
     * endosoGrupal=false en el fondeador (comportamiento por defecto), siempre
     * tiene un solo elemento (una solicitud por factura); con endosoGrupal=true,
     * puede tener varias facturas del mismo fondeador agrupadas en una sola
     * solicitud. El estado de aprobación real de cada factura vive en
     * FacturaNegociada (dentro de Negociacion); "estado" acá es un resumen
     * recalculado a partir de esas facturas.
     */
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
