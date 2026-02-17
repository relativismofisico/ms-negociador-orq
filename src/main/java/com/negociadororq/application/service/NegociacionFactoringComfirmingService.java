package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;

public interface NegociacionFactoringComfirmingService {
    NegociacionResponseDto crearNegociacion(NegociacionRequestDto negociacion);
}
