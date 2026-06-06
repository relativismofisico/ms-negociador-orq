package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;

public interface EndosoDescuentoService {
    EndosoResponseDto registrarEndoso(EndosoRequestDto request);
}
