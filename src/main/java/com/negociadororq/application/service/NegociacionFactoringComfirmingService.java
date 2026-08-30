package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;

/**
 * Crea una negociación de Factoring o Comfirming a partir de las facturas
 * solicitadas, validando reglas de negocio y consumiendo los cupos
 * correspondientes.
 */
public interface NegociacionFactoringComfirmingService {

    /**
     * @param negociacion datos de la negociación solicitada
     * @return el resultado de la negociación creada
     */
    NegociacionResponseDto crearNegociacion(NegociacionRequestDto negociacion);
}

