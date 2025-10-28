package com.negociadororq.domain.ports.in;

import com.negociadororq.domain.model.Negociacion;

public interface CrearNegociacionUseCase {
    Negociacion crearNegociacion(String rutEmpresa, String rolEmpresaFactura, String tipoOperacion);
}
