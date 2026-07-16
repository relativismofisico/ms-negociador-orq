package com.negociadororq.infrastructure.persistence.entity;

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
 * Copia de solo lectura de la tabla company_operation (dueña: ms-registroempresa-neg,
 * misma base de datos física compartida). Indica qué operaciones (FACTORING/CONFIRMING/
 * FONDEADOR) tiene habilitadas cada empresa.
 */
@Entity
@Table(name = "company_operation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ide_company_operation")
    private Long id;

    @Column(name = "operation_type")
    private String operationType;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "enabled")
    private Boolean enabled;
}
