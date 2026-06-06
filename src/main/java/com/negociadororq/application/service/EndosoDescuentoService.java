package com.negociadororq.application.service;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;

public interface EndosoDescuentoService {
    EndosoResponseDto registrarEndoso(EndosoRequestDto request);
}

