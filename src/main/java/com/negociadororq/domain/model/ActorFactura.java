package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rol de una empresa dentro de una factura puntual (pagador, proveedor, etc.).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActorFactura {

    private String rutEmpresa;
    private String rolEmpresaFactura;
}

