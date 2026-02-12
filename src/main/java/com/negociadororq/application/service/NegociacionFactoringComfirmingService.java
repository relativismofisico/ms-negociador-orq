package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;

import java.util.List;

public interface NegociacionFactoringComfirmingService {
    NegociacionResponseDto crearNegociacion(NegociacionRequestDto negociacion);
}
