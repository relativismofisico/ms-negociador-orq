package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import org.springframework.transaction.annotation.Transactional;

public interface EndosoDescuentoService {
    EndosoResponseDto registrarEndoso(EndosoRequestDto request);
}
