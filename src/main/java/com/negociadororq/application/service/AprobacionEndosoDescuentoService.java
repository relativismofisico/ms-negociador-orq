package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.AprobacionResponseDto;

public interface AprobacionEndosoDescuentoService {
    AprobacionResponseDto aprobar(AprobacionRequestDto request);
}
