package com.negociadororq.domain.model;

import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Solicitud de endoso de descuento sobre una o varias facturas del mismo fondeador,
 * con su documento firmado y el historial de aprobaciones de cada actor involucrado.
 */
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

    /**
     * CUFE (//cbc:UUID del XML DIAN, clave única global de cada factura) de las
     * facturas cubiertas por este endoso. Array PARALELO a {@link #numerosFactura}:
     * mismo orden y tamaño, {@code cufes.get(i)} es el CUFE de
     * {@code numerosFactura.get(i)}. Se puebla al crear el endoso a partir de la
     * factura ya cargada. Un elemento puede ser {@code null} si esa factura no
     * tiene CUFE (dato legacy); en endosos creados antes de esta versión la lista
     * entera viene {@code null}. Lo consume ms-consultasFacturas-neg para resolver
     * la factura exacta sin depender del {@code numeroFactura}, que es el
     * consecutivo del emisor y no es único global.
     */
    private List<String> cufes;

    private String rutActorEndosa;
    private String rutProveedor;
    private String rutPagador;
    private String rutFondeador;
    private Operacion tipoOperacion;
    private EstadoEndoso estado;
    @ToString.Exclude
    private String otp;
    private List<AprobacionActorDto> aprobaciones;
    private String documentoUrl;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaAprobacion;
    private LocalDateTime fechaActualizacion;
    private String documentoHtml;
    private byte[] documentoPdf;
    private String nombreArchivoPdf;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}
