package com.negociadororq.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "funder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FondeadorQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funder_id")
    private Long funderId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "payer_id")
    private Long payerId;

    @Column(name = "payer_quota")
    private BigDecimal payerQuota;

    @Column(name = "supplier_quota")
    private BigDecimal supplierQuota;

    @Column(name = "payer_current_quota")
    private BigDecimal payerCurrentQuota;

    @Column(name = "supplier_current_quota")
    private BigDecimal supplierCurrentQuota;

    @Column(name = "payer_rate")
    private BigDecimal payerRate;

    @Column(name = "supplier_rate")
    private BigDecimal supplierRate;

    @Column(name = "estado")
    private String estado;

    @Column(name = "origen")
    private String origen;

    @Column(name = "aprobado_por_fondeador")
    private Boolean aprobadoPorFondeador;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "endoso_grupal")
    private Boolean endosoGrupal;

    @Column(name = "rentabilidad_minima_activa")
    private Boolean rentabilidadMinimaActiva;

    @Column(name = "rentabilidad_minima")
    private BigDecimal rentabilidadMinima;

    @Column(name = "tipo_descuento")
    private String tipoDescuento;

    @Column(name = "tipo_tasa")
    private String tipoTasa;

    @Column(name = "tarifa_plena")
    private BigDecimal tarifaPlena;

    @Column(name = "vigencia_descuento")
    private String vigenciaDescuento;

    @Column(name = "dias_vigencia")
    private Integer diasVigencia;

    @Column(name = "porcentaje_reserva")
    private BigDecimal porcentajeReserva;
}
