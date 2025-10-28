package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActorFactura {

    private String rutEmpresa;       // NIT/RUT leído del XML
    private String rolEmpresaFactura;
}