package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;

public interface AprobacionNegociacionService {
    AprobacionResponseDto aprobar(AprobacionRequestDto request);
}
