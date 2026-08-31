package com.negociadororq.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistencia JPA de una {@link com.negociadororq.domain.model.Company}.
 */
@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ide_company")
    private Long ideCompany;

    @Column(name = "name_company")
    private String nameCompany;

    @Column(name = "nit", unique = true)
    private String nit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_code")
    private CityEntity city;
}
