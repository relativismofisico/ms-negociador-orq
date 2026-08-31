package com.negociadororq.infrastructure.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistencia JPA de una oferta de fondeo indirecto: la tasa y plantilla de cupo que un
 * fondeador ofrece para un pagador, base de los cupos individuales por proveedor
 * ({@link IndirectFunderOfferSupplierQuotaEntity}).
 */
@Entity
@Table(name = "indirect_funder_offer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOfferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funder_id")
    private Long funderId;

    @Column(name = "payer_id")
    private Long payerId;

    @Column(name = "payer_rate")
    private BigDecimal payerRate;

    @Column(name = "supplier_quota_template")
    private BigDecimal supplierQuotaTemplate;

    @Column(name = "estado")
    private String estado;

    @Column(name = "origen")
    private String origen;

    @Column(name = "aprobado_por_fondeador")
    private Boolean aprobadoPorFondeador;

    @Column(name = "activo")
    private Boolean activo;
}
