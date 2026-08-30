package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos básicos de una empresa, tal como los expone `ms-registroempresa-neg` a este
 * microservicio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    private Long ideCompany;
    private String nameCompany;
    private String nit;
    private String ciudad;
}
