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
 * Copia de solo lectura de la tabla company_section_verification (dueña:
 * ms-registroempresa-neg, misma base de datos física compartida). Indica qué
 * secciones de la pantalla de aprobación de empresa (ej. políticas de pago de
 * tesorería, cuentas bancarias) ya verificó el administrador de la plataforma.
 */
@Entity
@Table(name = "company_section_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySectionVerificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "section_key")
    private String sectionKey;
}
