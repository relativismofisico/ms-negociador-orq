package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionResponseDto;
import com.negociadororq.domain.model.PendingOtp;

public interface AprobacionInternaService {
    AprobacionResponseDto aprobarInterno(PendingOtp pending);
}

