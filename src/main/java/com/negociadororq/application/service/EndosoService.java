package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.EndosoRequestDto;
import com.negociadororq.application.controller.dto.EndosoResponseDto;
import org.springframework.transaction.annotation.Transactional;

public interface EndosoService {
    @Transactional
    EndosoResponseDto registrarEndoso(EndosoRequestDto request);
    EndosoResponseDto aprobarEndoso(String idEndoso, String rol, boolean aprobado);
}
