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
 * Persistencia JPA del cupo de un proveedor específico dentro de una
 * {@link IndirectFunderOfferEntity}, con su propia tasa y saldo disponible.
 */
@Entity
@Table(name = "indirect_funder_offer_supplier_quota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndirectFunderOfferSupplierQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "quota")
    private BigDecimal quota;

    @Column(name = "current_quota")
    private BigDecimal currentQuota;

    @Column(name = "original_quota")
    private BigDecimal originalQuota;
}
