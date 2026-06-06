package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;

public interface NegociacionFactoringComfirmingService {
    NegociacionResponseDto crearNegociacion(NegociacionRequestDto negociacion);
}

