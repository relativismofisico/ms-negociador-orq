package com.negociadororq.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "payer_quota", nullable = false)
    private BigDecimal payerQuota;



    // Estos tres campos almacenan RUTs, no IDs
    @Column(name = "funder_id", nullable = false)
    private Long funderId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "payer_current_quota")
    private BigDecimal payerCurrentQuota;


}
