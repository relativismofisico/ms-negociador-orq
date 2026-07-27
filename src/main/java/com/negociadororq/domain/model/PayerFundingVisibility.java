package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Toggle único y general de una empresa pagadora ("mostrar a mis proveedores
 * mis fondeadores de factoring indirecto"). Sin fila persistida, equivale a
 * {@code habilitado=false}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayerFundingVisibility {

    private Long companyId;
    private Boolean habilitado;
}
