package com.negociadororq.application.service;

import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;

public interface DocumentoEndosoService {

    void generarDocumentoFormal(Endoso endoso, Negociacion negociacion, Factura factura, FacturaNegociada facturaNegociada);
}
