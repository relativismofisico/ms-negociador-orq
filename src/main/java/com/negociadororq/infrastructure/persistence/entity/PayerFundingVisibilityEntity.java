package com.negociadororq.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistencia JPA de la visibilidad de fondeo habilitada para un pagador (si sus proveedores
 * pueden ver ofertas de fondeo indirecto).
 */
@Entity
@Table(name = "payer_funding_visibility")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayerFundingVisibilityEntity {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "habilitado")
    private Boolean habilitado;
}
