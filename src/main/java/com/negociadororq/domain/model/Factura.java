package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class Factura {

    private String id;
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
    private String cufe;
    private String estadoNegociacion;
    private String estadoFirma;
    private String negociacionId;
    private List<ActorFactura> actores;
    private String usuarioCargaFactura;
    private String empresaCargaFactura;
}
