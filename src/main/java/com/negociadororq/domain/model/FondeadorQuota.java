package com.negociadororq.domain.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cupo y condiciones (tasa, descuento, vigencia) que un fondeador asigna a un pagador
 * o a un proveedor puntual, junto con cuánto de ese cupo lleva consumido.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FondeadorQuota {

    private Long id;
    private Long funderId;
    private Long supplierId;
    private Long payerId;
    private BigDecimal payerQuota;
    private BigDecimal supplierQuota;
    private BigDecimal payerCurrentQuota;
    private BigDecimal supplierCurrentQuota;
    private BigDecimal payerRate;
    private BigDecimal supplierRate;
    private String estado;
    private String origen;
    private Boolean aprobadoPorFondeador;
    private Boolean activo;
    private Boolean endosoGrupal;
    private Boolean rentabilidadMinimaActiva;
    private BigDecimal rentabilidadMinima;
    private String tipoDescuento;
    private String tipoTasa;
    private BigDecimal tarifaPlena;
    private String vigenciaDescuento;
    private Integer diasVigencia;
    private BigDecimal porcentajeReserva;
    private Long version;
}
