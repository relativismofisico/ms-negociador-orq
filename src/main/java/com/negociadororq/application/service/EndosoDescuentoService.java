package com.negociadororq.application.service;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;

/**
 * Registra el endoso de descuento de una o más facturas negociadas.
 */
public interface EndosoDescuentoService {

    /**
     * @param request datos del endoso a registrar
     * @return el resultado del registro del endoso
     */
    EndosoResponseDto registrarEndoso(EndosoRequestDto request);
}

