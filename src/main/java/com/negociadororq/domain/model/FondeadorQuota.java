package com.negociadororq.domain.model;

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
public class FondeadorQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "funder_id")
    private Long funderId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "payer_id")
    private Long payerId;

    /**
     * Cupos máximos
     */
    @Column(name = "payer_quota")
    private BigDecimal payerQuota;

    @Column(name = "supplier_quota")
    private BigDecimal supplierQuota;

    /**
     * Cupos disponibles actuales
     */
    @Column(name = "payer_current_quota")
    private BigDecimal payerCurrentQuota;

    @Column(name = "supplier_current_quota")
    private BigDecimal supplierCurrentQuota;


}
