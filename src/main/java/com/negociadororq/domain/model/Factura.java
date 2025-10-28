package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "Facturas")
@RequiredArgsConstructor
@AllArgsConstructor
public class Factura {
    @Id
    private String id;

    @Field("numeroFactura")
    @Indexed(unique = true)   // <-- asegura unicidad en Mongo
    private String numeroFactura;

    private String codigoAlternoFactura;

    //Datos Pagador
    private String tipoIdentificacionPagador;
    private String rutPagador;
    private String rutAlternoPagador;
    private String nombrePagador;

    //Datos Proveedor
    private String tipoIdentificacionProveedor;
    private String rutProveedor;
    private String rutAlternoProveedor;
    private String nombreProveedor;

    //Datos Emision Factura
    private String tipoDocumento;
    private LocalDate fechaEmisionFactura;
    private LocalDate  fechaRadicacionFactura;
    private LocalDate  fechavencimientoFactura;
    private LocalDate  fechaEsperadaPagoPagador;

    //Datos Numericos Factura
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
    private LocalDate  fechaExtendidaConCosto;
    private LocalDate  fechaNegociacionFactura;
    private BigDecimal valorDescuentoPagador;
    private String originalFileName;
    private String pdfFileName;

    @Field("cufe")
    @Indexed(unique = true)
    private String cufe;

    private String estadoNegociacion;

    private String estadoFirma;

    private List<ActorFactura> actores;
    private String usuarioCargaFactura;
    private String empresaCargaFactura;
}
