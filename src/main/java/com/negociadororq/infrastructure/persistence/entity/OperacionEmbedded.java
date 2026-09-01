package com.negociadororq.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Forma de persistencia embebida de {@link com.negociadororq.domain.model.Operacion}
 * dentro de {@link NegociacionDocument}. Mismos campos que el modelo de dominio, pero
 * como tipo propio de infraestructura, para que el documento Mongo no comparta la
 * clase de dominio como su propio shape de sub-documento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperacionEmbedded {

    private String tipo;
    private String clase;
}
