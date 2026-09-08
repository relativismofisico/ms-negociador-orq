package com.negociadororq.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.negociadororq.domain.model.ActorFactura;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Persistencia MongoDB de una {@link com.negociadororq.domain.model.Factura}.
 */
@Data
@Document(collection = "Facturas")
@RequiredArgsConstructor
@AllArgsConstructor
public class FacturaDocument {

    @Id
    private String id;

    // numeroFactura (//cbc:ID del XML DIAN) es el consecutivo del EMISOR: único por
    // emisor, NO global. La clave única global es `cufe` (//cbc:UUID). El
    // @Indexed(unique=true) que estaba acá era incorrecto (y además inerte:
    // auto-index-creation está apagado).
    @Field("numeroFactura")
    private String numeroFactura;

    private String codigoAlternoFactura;
    private String tipoIdentificacionPagador;
    private String rutPagador;
    private String rutAlternoPagador;
    private String nombrePagador;
    private String tipoIdentificacionProveedor;
    private String rutProveedor;
    private String rutAlternoProveedor;
    private String nombreProveedor;
    private String tipoDocumento;
    private LocalDate fechaEmisionFactura;
    private LocalDate fechaRadicacionFactura;
    private LocalDate fechavencimientoFactura;
    private LocalDate fechaEsperadaPagoPagador;
    private String moneda;
    private BigDecimal valorFactura;
    private BigDecimal ivaFactura;
    private BigDecimal valorRetencionFactura;
    private BigDecimal valorNotaCredito;
    private String observacionesFactura;
    private BigDecimal valorAnticiposFactura;
    private BigDecimal valorNetoFactura;
    private String tipoBeneficioFactura;
    private String observaciones;
    private LocalDate fechaExtendidaConCosto;
    private LocalDate fechaNegociacionFactura;
    private BigDecimal valorDescuentoPagador;
    private String originalFileName;
    private String pdfFileName;

    @Field("cufe")
    @Indexed(unique = true)
    private String cufe;

    private String estadoNegociacion;
    private String estadoFirma;
    private String negociacionId;
    private List<ActorFactura> actores;
    private String usuarioCargaFactura;
    private String empresaCargaFactura;
}
